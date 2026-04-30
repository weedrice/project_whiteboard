package com.weedrice.whiteboard.domain.mqueue.scheduler;

import com.weedrice.whiteboard.domain.mqueue.MessageQueuePolicy;
import com.weedrice.whiteboard.domain.mqueue.entity.MessageQueue;
import com.weedrice.whiteboard.domain.mqueue.repository.MessageQueueRepository;
import com.weedrice.whiteboard.domain.mqueue.service.MqueueService;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

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
        when(messageQueueRepository.recoverStaleProcessingMessages(any(), eq(MessageQueuePolicy.MAX_RETRY_COUNT)))
                .thenReturn(2);
        when(messageQueueRepository.findByStatusAndRetryCountLessThanAndDeliveryMethod(
                eq("PENDING"), eq(MessageQueuePolicy.MAX_RETRY_COUNT), eq("EMAIL"), any(Pageable.class)))
                .thenReturn(List.of());

        mqueueScheduler.processMessageQueue();

        var inOrder = inOrder(messageQueueRepository);
        inOrder.verify(messageQueueRepository)
                .recoverStaleProcessingMessages(any(), eq(MessageQueuePolicy.MAX_RETRY_COUNT));
        inOrder.verify(messageQueueRepository).findByStatusAndRetryCountLessThanAndDeliveryMethod(
                eq("PENDING"), eq(MessageQueuePolicy.MAX_RETRY_COUNT), eq("EMAIL"), any(Pageable.class));
    }

    @Test
    @DisplayName("scheduler reads pending messages in stable FIFO order")
    void processMessageQueue_usesStableFifoPageRequest() {
        when(messageQueueRepository.recoverStaleProcessingMessages(any(), eq(MessageQueuePolicy.MAX_RETRY_COUNT)))
                .thenReturn(0);
        when(messageQueueRepository.findByStatusAndRetryCountLessThanAndDeliveryMethod(
                eq("PENDING"), eq(MessageQueuePolicy.MAX_RETRY_COUNT), eq("EMAIL"), any(Pageable.class)))
                .thenReturn(List.of());

        mqueueScheduler.processMessageQueue();

        var pageableCaptor = forClass(Pageable.class);
        verify(messageQueueRepository).findByStatusAndRetryCountLessThanAndDeliveryMethod(
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
        when(messageQueueRepository.recoverStaleProcessingMessages(any(), eq(MessageQueuePolicy.MAX_RETRY_COUNT)))
                .thenReturn(0);
        when(messageQueueRepository.findByStatusAndRetryCountLessThanAndDeliveryMethod(
                eq("PENDING"), eq(MessageQueuePolicy.MAX_RETRY_COUNT), eq("EMAIL"), any(Pageable.class)))
                .thenReturn(List.of());

        mqueueScheduler.processMessageQueue();

        verify(messageQueueRepository).findByStatusAndRetryCountLessThanAndDeliveryMethod(
                eq("PENDING"), eq(MessageQueuePolicy.MAX_RETRY_COUNT), eq("EMAIL"), any(Pageable.class));
        verify(messageQueueRepository, never()).claimForProcessing(any(), any(Integer.class), any());
        verify(mqueueService, never()).sendEmail(any());
    }

    @Test
    @DisplayName("scheduler releases processing lease when async dispatch is rejected")
    void processMessageQueue_handlesDispatchRejection() {
        MessageQueue message = buildMessageQueue(1L, "EMAIL");
        when(messageQueueRepository.recoverStaleProcessingMessages(any(), eq(MessageQueuePolicy.MAX_RETRY_COUNT)))
                .thenReturn(0);
        when(messageQueueRepository.findByStatusAndRetryCountLessThanAndDeliveryMethod(
                eq("PENDING"), eq(MessageQueuePolicy.MAX_RETRY_COUNT), eq("EMAIL"), any(Pageable.class)))
                .thenReturn(List.of(message));
        when(messageQueueRepository.claimForProcessing(eq(1L), eq(MessageQueuePolicy.MAX_RETRY_COUNT), any()))
                .thenReturn(1);
        doThrow(new TaskRejectedException("rejected")).when(mqueueService).sendEmail(1L);

        mqueueScheduler.processMessageQueue();

        verify(mqueueService).sendEmail(1L);
        verify(mqueueService).recoverRejectedDispatch(1L);
    }

    private MessageQueue buildMessageQueue(Long queueId, String deliveryMethod) {
        User user = User.builder().email("user@test.com").build();
        MessageQueue message = MessageQueue.builder()
                .targetUser(user)
                .deliveryMethod(deliveryMethod)
                .content("content")
                .build();
        ReflectionTestUtils.setField(message, "queueId", queueId);
        return message;
    }
}
