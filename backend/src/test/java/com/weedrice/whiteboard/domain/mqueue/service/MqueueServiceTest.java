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
        LocalDateTime leaseStartedAt = LocalDateTime.now();
        User user = User.builder().email("user@test.com").build();
        MessageQueue message = processingMessage(user, leaseStartedAt);
        when(messageQueueRepository.findByIdWithTargetUser(1L)).thenReturn(Optional.of(message));
        mockLeaseRenewal(1L, leaseStartedAt, message);
        when(messageQueueRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(message));
        mockTransactionCallback();

        mqueueService.sendEmail(1L, leaseStartedAt);

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
            LocalDateTime leaseStartedAt = LocalDateTime.now();
            User user = User.builder().email("user@test.com").build();
            MessageQueue message = processingMessage(user, leaseStartedAt);
            when(messageQueueRepository.findByIdWithTargetUser(1L)).thenReturn(Optional.of(message));
            mockLeaseRenewal(1L, leaseStartedAt, message);
            when(messageQueueRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(message));
            mockTransactionCallback();

            mqueueService.sendEmail(1L, leaseStartedAt);

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
        LocalDateTime leaseStartedAt = LocalDateTime.now();
        User user = User.builder().email("user@test.com").build();
        MessageQueue message = processingMessage(user, leaseStartedAt);
        when(messageQueueRepository.findByIdWithTargetUser(1L)).thenReturn(Optional.of(message));
        mockLeaseRenewal(1L, leaseStartedAt, message);
        when(messageQueueRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(message));
        doThrow(new BusinessException(ErrorCode.EMAIL_SEND_FAILED))
                .when(emailService).sendEmail(eq("user@test.com"), eq("[noviIs] Notification"), eq("<p>Hello</p>"));
        mockTransactionCallback();

        mqueueService.sendEmail(1L, leaseStartedAt);

        verify(messageQueueRepository).save(message);
        assertThat(message.getStatus()).isEqualTo("PENDING");
        assertThat(message.getRetryCount()).isEqualTo(1);
        assertThat(message.getProcessingStartedAt()).isNull();
    }

    @Test
    @DisplayName("sendEmail skips SMTP call when lease renewal fails after stale recovery")
    void sendEmail_leaseRenewalFails_skipsSmtpCall() {
        LocalDateTime leaseStartedAt = LocalDateTime.now().minusMinutes(6);
        User user = User.builder().email("user@test.com").build();
        MessageQueue processingMessage = processingMessage(user, leaseStartedAt);

        when(messageQueueRepository.findByIdWithTargetUser(1L)).thenReturn(Optional.of(processingMessage));
        when(messageQueueRepository.renewProcessingLeaseIfCurrent(eq(1L), eq(leaseStartedAt), any()))
                .thenReturn(0);

        mqueueService.sendEmail(1L, leaseStartedAt);

        verify(emailService, never()).sendEmail(any(), any(), any());
        verify(messageQueueRepository, never()).save(any(MessageQueue.class));
        assertThat(processingMessage.getStatus()).isEqualTo("PROCESSING");
        assertThat(processingMessage.getProcessingStartedAt()).isEqualTo(leaseStartedAt);
    }

    @Test
    @DisplayName("sendEmail skips failure handling when lease renewal fails before SMTP")
    void sendEmail_leaseRenewalFailsBeforeFailure_skipsFailureHandling() {
        LocalDateTime leaseStartedAt = LocalDateTime.now().minusMinutes(6);
        User user = User.builder().email("user@test.com").build();
        MessageQueue processingMessage = processingMessage(user, leaseStartedAt);

        when(messageQueueRepository.findByIdWithTargetUser(1L)).thenReturn(Optional.of(processingMessage));
        when(messageQueueRepository.renewProcessingLeaseIfCurrent(eq(1L), eq(leaseStartedAt), any()))
                .thenReturn(0);

        mqueueService.sendEmail(1L, leaseStartedAt);

        verify(emailService, never()).sendEmail(any(), any(), any());
        verify(messageQueueRepository, never()).save(any(MessageQueue.class));
        assertThat(processingMessage.getStatus()).isEqualTo("PROCESSING");
        assertThat(processingMessage.getProcessingStartedAt()).isEqualTo(leaseStartedAt);
    }

    @Test
    @DisplayName("sendEmail skips send after stale recovery consumes retry budget")
    void sendEmail_recoveryConsumesRetryBudget_skipsSmtpCall() {
        LocalDateTime leaseStartedAt = LocalDateTime.now().minusMinutes(6);
        User user = User.builder().email("user@test.com").build();
        MessageQueue processingMessage = processingMessage(user, leaseStartedAt);

        when(messageQueueRepository.findByIdWithTargetUser(1L)).thenReturn(Optional.of(processingMessage));
        when(messageQueueRepository.renewProcessingLeaseIfCurrent(eq(1L), eq(leaseStartedAt), any()))
                .thenReturn(0);

        mqueueService.sendEmail(1L, leaseStartedAt);

        verify(emailService, never()).sendEmail(any(), any(), any());
        verify(messageQueueRepository, never()).save(any(MessageQueue.class));
        assertThat(processingMessage.getStatus()).isEqualTo("PROCESSING");
        assertThat(processingMessage.getProcessingStartedAt()).isEqualTo(leaseStartedAt);
    }

    @Test
    @DisplayName("sendEmail skips message without current processing lease")
    void sendEmail_withoutCurrentProcessingLease_skipsSend() {
        LocalDateTime leaseStartedAt = LocalDateTime.now();
        MessageQueue message = processingMessage(User.builder().email("user@test.com").build(), null);
        when(messageQueueRepository.findByIdWithTargetUser(1L)).thenReturn(Optional.of(message));

        mqueueService.sendEmail(1L, leaseStartedAt);

        verify(messageQueueRepository, never()).renewProcessingLeaseIfCurrent(any(), any(), any());
        verify(emailService, never()).sendEmail(any(), any(), any());
    }

    @Test
    @DisplayName("recoverRejectedDispatch releases lease without retry increment")
    void recoverRejectedDispatch_revertsProcessingMessageToPendingWithoutRetryIncrement() {
        LocalDateTime leaseStartedAt = LocalDateTime.now();
        MessageQueue message = processingMessage(User.builder().email("user@test.com").build(), leaseStartedAt);
        when(messageQueueRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(message));

        mqueueService.recoverRejectedDispatch(1L, leaseStartedAt);

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

    private void mockLeaseRenewal(Long queueId, LocalDateTime expectedLease, MessageQueue message) {
        when(messageQueueRepository.renewProcessingLeaseIfCurrent(eq(queueId), eq(expectedLease), any()))
                .thenAnswer(invocation -> {
                    LocalDateTime renewedAt = invocation.getArgument(2);
                    ReflectionTestUtils.setField(message, "processingStartedAt", renewedAt);
                    return 1;
                });
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
