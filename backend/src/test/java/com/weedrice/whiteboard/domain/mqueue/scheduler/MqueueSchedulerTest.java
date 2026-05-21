package com.weedrice.whiteboard.domain.mqueue.scheduler;

import com.weedrice.whiteboard.domain.mqueue.MessageQueuePolicy;
import com.weedrice.whiteboard.domain.mqueue.repository.MessageQueueRepository;
import com.weedrice.whiteboard.domain.mqueue.repository.MessageQueueRepository.EmailDispatchProjection;
import com.weedrice.whiteboard.domain.mqueue.service.MqueueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqueueSchedulerTest {

    @Mock
    private MessageQueueRepository messageQueueRepository;
    @Mock
    private MqueueService mqueueService;

    @InjectMocks
    private MqueueScheduler mqueueScheduler;

    @Test
    @DisplayName("scheduler recovers stale processing messages before claiming pending work")
    void processMessageQueue_recoversStaleMessagesFirst() {
        when(messageQueueRepository.recoverStaleProcessingMessages(
                any(), eq(MessageQueuePolicy.MAX_RETRY_COUNT), any()))
                .thenReturn(2);
        when(messageQueueRepository.findPendingQueueIdsByStatusAndRetryCountLessThanAndDeliveryMethod(
                eq("PENDING"), eq(MessageQueuePolicy.MAX_RETRY_COUNT), eq("EMAIL"), any(Pageable.class)))
                .thenReturn(List.of());

        mqueueScheduler.processMessageQueue();

        var inOrder = inOrder(messageQueueRepository);
        inOrder.verify(messageQueueRepository)
                .recoverStaleProcessingMessages(any(), eq(MessageQueuePolicy.MAX_RETRY_COUNT), any());
        inOrder.verify(messageQueueRepository).findPendingQueueIdsByStatusAndRetryCountLessThanAndDeliveryMethod(
                eq("PENDING"), eq(MessageQueuePolicy.MAX_RETRY_COUNT), eq("EMAIL"), any(Pageable.class));
    }

    @Test
    @DisplayName("scheduler reads pending messages in stable FIFO order")
    void processMessageQueue_usesStableFifoPageRequest() {
        when(messageQueueRepository.recoverStaleProcessingMessages(
                any(), eq(MessageQueuePolicy.MAX_RETRY_COUNT), any()))
                .thenReturn(0);
        when(messageQueueRepository.findPendingQueueIdsByStatusAndRetryCountLessThanAndDeliveryMethod(
                eq("PENDING"), eq(MessageQueuePolicy.MAX_RETRY_COUNT), eq("EMAIL"), any(Pageable.class)))
                .thenReturn(List.of());

        mqueueScheduler.processMessageQueue();

        var pageableCaptor = forClass(Pageable.class);
        verify(messageQueueRepository).findPendingQueueIdsByStatusAndRetryCountLessThanAndDeliveryMethod(
                eq("PENDING"), eq(MessageQueuePolicy.MAX_RETRY_COUNT), eq("EMAIL"), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(50);
        assertThat(pageable.getSort().stream()
                .map(order -> order.getProperty())
                .toList())
                .containsExactly("requestedAt", "queueId");
        assertThat(pageable.getSort().getOrderFor("requestedAt")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("requestedAt").isAscending()).isTrue();
        assertThat(pageable.getSort().getOrderFor("queueId")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("queueId").isAscending()).isTrue();
    }

    @Test
    @DisplayName("scheduler queries only email messages")
    void processMessageQueue_queriesOnlyEmailMessages() {
        when(messageQueueRepository.recoverStaleProcessingMessages(
                any(), eq(MessageQueuePolicy.MAX_RETRY_COUNT), any()))
                .thenReturn(0);
        when(messageQueueRepository.findPendingQueueIdsByStatusAndRetryCountLessThanAndDeliveryMethod(
                eq("PENDING"), eq(MessageQueuePolicy.MAX_RETRY_COUNT), eq("EMAIL"), any(Pageable.class)))
                .thenReturn(List.of());

        mqueueScheduler.processMessageQueue();

        verify(messageQueueRepository).findPendingQueueIdsByStatusAndRetryCountLessThanAndDeliveryMethod(
                eq("PENDING"), eq(MessageQueuePolicy.MAX_RETRY_COUNT), eq("EMAIL"), any(Pageable.class));
        verify(messageQueueRepository, never()).claimForProcessing(any(), any(Integer.class), any());
        verify(messageQueueRepository, never()).findEmailDispatchesByQueueIdIn(anyList());
        verify(mqueueService, never()).sendEmail(any(EmailDispatchProjection.class), any());
    }

    @Test
    @DisplayName("scheduler releases processing lease when async dispatch is rejected")
    void processMessageQueue_handlesDispatchRejection() {
        EmailDispatchProjection dispatch = emailDispatch(1L, "user@test.com", "<p>Hello</p>");
        when(messageQueueRepository.recoverStaleProcessingMessages(
                any(), eq(MessageQueuePolicy.MAX_RETRY_COUNT), any()))
                .thenReturn(0);
        when(messageQueueRepository.findPendingQueueIdsByStatusAndRetryCountLessThanAndDeliveryMethod(
                eq("PENDING"), eq(MessageQueuePolicy.MAX_RETRY_COUNT), eq("EMAIL"), any(Pageable.class)))
                .thenReturn(List.of(1L));
        when(messageQueueRepository.claimForProcessing(eq(1L), eq(MessageQueuePolicy.MAX_RETRY_COUNT), any()))
                .thenReturn(1);
        when(messageQueueRepository.findEmailDispatchesByQueueIdIn(List.of(1L))).thenReturn(List.of(dispatch));
        doThrow(new TaskRejectedException("rejected")).when(mqueueService).sendEmail(eq(dispatch), any());

        mqueueScheduler.processMessageQueue();

        var claimedAtCaptor = forClass(LocalDateTime.class);
        verify(messageQueueRepository).claimForProcessing(
                eq(1L), eq(MessageQueuePolicy.MAX_RETRY_COUNT), claimedAtCaptor.capture());
        LocalDateTime claimedAt = claimedAtCaptor.getValue();
        verify(mqueueService).sendEmail(dispatch, claimedAt);
        verify(mqueueService).recoverRejectedDispatch(1L, claimedAt);
    }

    @Test
    @DisplayName("scheduler bulk fetches and dispatches only claimed queue ids in FIFO order")
    void processMessageQueue_bulkFetchesClaimedIdsAndDispatchesInClaimedOrder() {
        EmailDispatchProjection secondDispatch = emailDispatch(2L, "second@test.com", "second");
        EmailDispatchProjection firstDispatch = emailDispatch(1L, "first@test.com", "first");
        when(messageQueueRepository.recoverStaleProcessingMessages(
                any(), eq(MessageQueuePolicy.MAX_RETRY_COUNT), any()))
                .thenReturn(0);
        when(messageQueueRepository.findPendingQueueIdsByStatusAndRetryCountLessThanAndDeliveryMethod(
                eq("PENDING"), eq(MessageQueuePolicy.MAX_RETRY_COUNT), eq("EMAIL"), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L, 3L));
        when(messageQueueRepository.claimForProcessing(eq(1L), eq(MessageQueuePolicy.MAX_RETRY_COUNT), any()))
                .thenReturn(1);
        when(messageQueueRepository.claimForProcessing(eq(2L), eq(MessageQueuePolicy.MAX_RETRY_COUNT), any()))
                .thenReturn(1);
        when(messageQueueRepository.claimForProcessing(eq(3L), eq(MessageQueuePolicy.MAX_RETRY_COUNT), any()))
                .thenReturn(0);
        when(messageQueueRepository.findEmailDispatchesByQueueIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(secondDispatch, firstDispatch));

        mqueueScheduler.processMessageQueue();

        verify(messageQueueRepository).findEmailDispatchesByQueueIdIn(List.of(1L, 2L));
        var inOrder = inOrder(mqueueService);
        inOrder.verify(mqueueService).sendEmail(eq(firstDispatch), any());
        inOrder.verify(mqueueService).sendEmail(eq(secondDispatch), any());
    }

    @Test
    @DisplayName("scheduler releases claimed message when dispatch projection is missing")
    void processMessageQueue_recoversClaimedMessageMissingProjection() {
        when(messageQueueRepository.recoverStaleProcessingMessages(
                any(), eq(MessageQueuePolicy.MAX_RETRY_COUNT), any()))
                .thenReturn(0);
        when(messageQueueRepository.findPendingQueueIdsByStatusAndRetryCountLessThanAndDeliveryMethod(
                eq("PENDING"), eq(MessageQueuePolicy.MAX_RETRY_COUNT), eq("EMAIL"), any(Pageable.class)))
                .thenReturn(List.of(1L));
        when(messageQueueRepository.claimForProcessing(eq(1L), eq(MessageQueuePolicy.MAX_RETRY_COUNT), any()))
                .thenReturn(1);
        when(messageQueueRepository.findEmailDispatchesByQueueIdIn(List.of(1L))).thenReturn(List.of());

        mqueueScheduler.processMessageQueue();

        var claimedAtCaptor = forClass(LocalDateTime.class);
        verify(messageQueueRepository).claimForProcessing(
                eq(1L), eq(MessageQueuePolicy.MAX_RETRY_COUNT), claimedAtCaptor.capture());
        verify(mqueueService).recoverRejectedDispatch(1L, claimedAtCaptor.getValue());
        verify(mqueueService, never()).sendEmail(any(EmailDispatchProjection.class), any());
    }

    private EmailDispatchProjection emailDispatch(Long queueId, String targetEmail, String content) {
        return new EmailDispatchProjection() {
            @Override
            public Long getQueueId() {
                return queueId;
            }

            @Override
            public String getTargetEmail() {
                return targetEmail;
            }

            @Override
            public String getContent() {
                return content;
            }
        };
    }

}
