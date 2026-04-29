package com.weedrice.whiteboard.domain.mqueue.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.weedrice.whiteboard.domain.mqueue.entity.MessageQueue;
import com.weedrice.whiteboard.domain.mqueue.repository.MessageQueueRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.email.EmailService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqueueServiceTest {

    @Mock
    private MessageQueueRepository messageQueueRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private MqueueService mqueueService;

    @Test
    @DisplayName("queueEmail saves message")
    void queueEmail_success() {
        User user = User.builder().build();

        mqueueService.queueEmail(user, "Test Email");

        verify(messageQueueRepository).save(any(MessageQueue.class));
    }

    @Test
    @DisplayName("sendEmail marks current lease as sent on success")
    void sendEmail_success() {
        User user = User.builder().email("user@test.com").build();
        MessageQueue message = processingMessage(user, LocalDateTime.now());
        when(messageQueueRepository.findByIdWithTargetUser(1L)).thenReturn(Optional.of(message));
        when(messageQueueRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(message));
        mockTransactionCallback();

        mqueueService.sendEmail(1L);

        verify(emailService).sendEmail("user@test.com", "[noviIs] Notification", "<p>Hello</p>");
        verify(messageQueueRepository).save(message);
        assertThat(message.getStatus()).isEqualTo("SENT");
        assertThat(message.getProcessingStartedAt()).isNull();
    }

    @Test
    @DisplayName("sendEmail logs queue id without recipient or content")
    void sendEmail_logsQueueIdWithoutRecipientOrContent() {
        Logger logger = (Logger) LoggerFactory.getLogger(MqueueService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            User user = User.builder().email("user@test.com").build();
            MessageQueue message = processingMessage(user, LocalDateTime.now());
            when(messageQueueRepository.findByIdWithTargetUser(1L)).thenReturn(Optional.of(message));
            when(messageQueueRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(message));
            mockTransactionCallback();

            mqueueService.sendEmail(1L);

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(logMessage -> logMessage.contains("queueId=1"))
                    .noneMatch(logMessage -> logMessage.contains("user@test.com"))
                    .noneMatch(logMessage -> logMessage.contains("<p>Hello</p>"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @DisplayName("sendEmail increments retry and requeues on failure")
    void sendEmail_failure() {
        User user = User.builder().email("user@test.com").build();
        MessageQueue message = processingMessage(user, LocalDateTime.now());
        when(messageQueueRepository.findByIdWithTargetUser(1L)).thenReturn(Optional.of(message));
        when(messageQueueRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(message));
        doThrow(new BusinessException(ErrorCode.EMAIL_SEND_FAILED))
                .when(emailService).sendEmail(eq("user@test.com"), eq("[noviIs] Notification"), eq("<p>Hello</p>"));
        mockTransactionCallback();

        mqueueService.sendEmail(1L);

        verify(messageQueueRepository).save(message);
        assertThat(message.getStatus()).isEqualTo("PENDING");
        assertThat(message.getRetryCount()).isEqualTo(1);
        assertThat(message.getProcessingStartedAt()).isNull();
    }

    @Test
    @DisplayName("sendEmail skips recovered pending message after late success")
    void sendEmail_successAfterLeaseRecovery_skipsRecoveredPendingMessage() {
        LocalDateTime leaseStartedAt = LocalDateTime.now().minusMinutes(6);
        User user = User.builder().email("user@test.com").build();
        MessageQueue processingMessage = processingMessage(user, leaseStartedAt);
        MessageQueue recoveredMessage = MessageQueue.builder()
                .targetUser(user)
                .deliveryMethod("EMAIL")
                .content("<p>Hello</p>")
                .build();
        ReflectionTestUtils.setField(recoveredMessage, "status", "PENDING");
        ReflectionTestUtils.setField(recoveredMessage, "retryCount", 1);

        when(messageQueueRepository.findByIdWithTargetUser(1L)).thenReturn(Optional.of(processingMessage));
        when(messageQueueRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(recoveredMessage));
        mockTransactionCallback();

        mqueueService.sendEmail(1L);

        verify(messageQueueRepository, never()).save(any(MessageQueue.class));
        assertThat(recoveredMessage.getStatus()).isEqualTo("PENDING");
        assertThat(recoveredMessage.getRetryCount()).isEqualTo(1);
        assertThat(recoveredMessage.getProcessingStartedAt()).isNull();
    }

    @Test
    @DisplayName("sendEmail skips recovered message after late failure")
    void sendEmail_failureAfterLeaseRecovery_skipsRecoveredMessage() {
        LocalDateTime leaseStartedAt = LocalDateTime.now().minusMinutes(6);
        User user = User.builder().email("user@test.com").build();
        MessageQueue processingMessage = processingMessage(user, leaseStartedAt);
        MessageQueue recoveredMessage = MessageQueue.builder()
                .targetUser(user)
                .deliveryMethod("EMAIL")
                .content("<p>Hello</p>")
                .build();
        ReflectionTestUtils.setField(recoveredMessage, "status", "PENDING");
        ReflectionTestUtils.setField(recoveredMessage, "retryCount", 1);

        when(messageQueueRepository.findByIdWithTargetUser(1L)).thenReturn(Optional.of(processingMessage));
        when(messageQueueRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(recoveredMessage));
        doThrow(new BusinessException(ErrorCode.EMAIL_SEND_FAILED))
                .when(emailService).sendEmail(eq("user@test.com"), eq("[noviIs] Notification"), eq("<p>Hello</p>"));
        mockTransactionCallback();

        mqueueService.sendEmail(1L);

        verify(messageQueueRepository, never()).save(any(MessageQueue.class));
        assertThat(recoveredMessage.getStatus()).isEqualTo("PENDING");
        assertThat(recoveredMessage.getRetryCount()).isEqualTo(1);
        assertThat(recoveredMessage.getProcessingStartedAt()).isNull();
    }

    @Test
    @DisplayName("sendEmail skips recovered failed message after late success")
    void sendEmail_successAfterFailedRecovery_skipsRecoveredFailedMessage() {
        LocalDateTime leaseStartedAt = LocalDateTime.now().minusMinutes(6);
        User user = User.builder().email("user@test.com").build();
        MessageQueue processingMessage = processingMessage(user, leaseStartedAt);
        MessageQueue recoveredMessage = MessageQueue.builder()
                .targetUser(user)
                .deliveryMethod("EMAIL")
                .content("<p>Hello</p>")
                .build();
        ReflectionTestUtils.setField(recoveredMessage, "status", "FAILED");
        ReflectionTestUtils.setField(recoveredMessage, "retryCount", 5);

        when(messageQueueRepository.findByIdWithTargetUser(1L)).thenReturn(Optional.of(processingMessage));
        when(messageQueueRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(recoveredMessage));
        mockTransactionCallback();

        mqueueService.sendEmail(1L);

        verify(messageQueueRepository, never()).save(any(MessageQueue.class));
        assertThat(recoveredMessage.getStatus()).isEqualTo("FAILED");
        assertThat(recoveredMessage.getRetryCount()).isEqualTo(5);
        assertThat(recoveredMessage.getProcessingStartedAt()).isNull();
    }

    @Test
    @DisplayName("recoverRejectedDispatch releases lease without retry increment")
    void recoverRejectedDispatch_revertsProcessingMessageToPendingWithoutRetryIncrement() {
        MessageQueue message = processingMessage(User.builder().email("user@test.com").build(), LocalDateTime.now());
        when(messageQueueRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(message));

        mqueueService.recoverRejectedDispatch(1L);

        verify(messageQueueRepository).save(message);
        assertThat(message.getStatus()).isEqualTo("PENDING");
        assertThat(message.getRetryCount()).isEqualTo(0);
        assertThat(message.getProcessingStartedAt()).isNull();
    }

    private void mockTransactionCallback() {
        doAnswer(invocation -> {
            Consumer<Object> consumer = invocation.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private MessageQueue processingMessage(User user, LocalDateTime processingStartedAt) {
        MessageQueue message = MessageQueue.builder()
                .targetUser(user)
                .deliveryMethod("EMAIL")
                .content("<p>Hello</p>")
                .build();
        ReflectionTestUtils.setField(message, "status", "PROCESSING");
        ReflectionTestUtils.setField(message, "processingStartedAt", processingStartedAt);
        return message;
    }
}
