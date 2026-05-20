package com.weedrice.whiteboard.domain.mqueue.scheduler;

import com.weedrice.whiteboard.domain.mqueue.MessageQueuePolicy;
import com.weedrice.whiteboard.domain.mqueue.repository.MessageQueueRepository;
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
        verify(mqueueService, never()).sendEmail(any(), any());
    }

    @Test
    @DisplayName("scheduler releases processing lease when async dispatch is rejected")
    void processMessageQueue_handlesDispatchRejection() {
        when(messageQueueRepository.recoverStaleProcessingMessages(
                any(), eq(MessageQueuePolicy.MAX_RETRY_COUNT), any()))
                .thenReturn(0);
        when(messageQueueRepository.findPendingQueueIdsByStatusAndRetryCountLessThanAndDeliveryMethod(
                eq("PENDING"), eq(MessageQueuePolicy.MAX_RETRY_COUNT), eq("EMAIL"), any(Pageable.class)))
                .thenReturn(List.of(1L));
        when(messageQueueRepository.claimForProcessing(eq(1L), eq(MessageQueuePolicy.MAX_RETRY_COUNT), any()))
                .thenReturn(1);
        doThrow(new TaskRejectedException("rejected")).when(mqueueService).sendEmail(eq(1L), any());

        mqueueScheduler.processMessageQueue();

        var claimedAtCaptor = forClass(LocalDateTime.class);
        verify(messageQueueRepository).claimForProcessing(
                eq(1L), eq(MessageQueuePolicy.MAX_RETRY_COUNT), claimedAtCaptor.capture());
        LocalDateTime claimedAt = claimedAtCaptor.getValue();
        verify(mqueueService).sendEmail(1L, claimedAt);
        verify(mqueueService).recoverRejectedDispatch(1L, claimedAt);
    }

}
