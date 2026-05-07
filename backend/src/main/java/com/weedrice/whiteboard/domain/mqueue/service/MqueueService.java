package com.weedrice.whiteboard.domain.mqueue.service;

import com.weedrice.whiteboard.domain.mqueue.MessageQueuePolicy;
import com.weedrice.whiteboard.domain.mqueue.entity.MessageQueue;
import com.weedrice.whiteboard.domain.mqueue.repository.MessageQueueRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqueueService {
    private static final String EMAIL_SUBJECT = "[noviIs] Notification";
    private static final int SEND_RESULT_PERSIST_ATTEMPTS = 3;

    private final MessageQueueRepository messageQueueRepository;
    private final EmailService emailService;
    private final TransactionTemplate transactionTemplate;

    @Transactional
    public void queueEmail(User user, String content) {
        MessageQueue message = MessageQueue.builder()
                .targetUser(user)
                .deliveryMethod("EMAIL")
                .content(content)
                .build();
        messageQueueRepository.save(message);
    }

    @Async("taskExecutor")
    public void sendEmail(Long queueId, LocalDateTime claimedAt) {
        MessageQueue message = messageQueueRepository.findByIdWithTargetUser(queueId).orElse(null);
        if (message == null || !isCurrentLease(message, claimedAt)) {
            return;
        }

        LocalDateTime renewedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        int renewed = messageQueueRepository.renewProcessingLeaseIfCurrent(queueId, claimedAt, renewedAt);
        if (renewed != 1) {
            log.warn("Skipped email send because processing lease changed before SMTP call: queueId={}", queueId);
            return;
        }

        boolean sentSuccessfully = false;

        try {
            log.info("Email sending attempt: queueId={}", queueId);
            emailService.sendEmail(message.getTargetUser().getEmail(), EMAIL_SUBJECT, message.getContent());
            sentSuccessfully = true;
            log.info("Email sent successfully: queueId={}", queueId);
        } catch (Exception e) {
            log.error("Email sending failed: queueId={}", queueId, e);
        }

        boolean finalSentSuccessfully = sentSuccessfully;
        persistSendResultWithRetry(queueId, renewedAt, finalSentSuccessfully);
    }

    @Transactional
    public void recoverRejectedDispatch(Long queueId, LocalDateTime claimedAt) {
        MessageQueue message = messageQueueRepository.findByIdForUpdate(queueId).orElse(null);
        if (message == null || !isCurrentLease(message, claimedAt)) {
            return;
        }
        message.releaseProcessingLease();
        messageQueueRepository.save(message);
    }

    private void persistSendResultWithRetry(Long queueId, LocalDateTime leaseStartedAt, boolean sentSuccessfully) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= SEND_RESULT_PERSIST_ATTEMPTS; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(status ->
                        persistSendResult(queueId, leaseStartedAt, sentSuccessfully));
                return;
            } catch (RuntimeException ex) {
                lastException = ex;
                log.error("Failed to persist email send result: queueId={}, attempt={}", queueId, attempt, ex);
            }
        }
        throw lastException;
    }

    private void persistSendResult(Long queueId, LocalDateTime leaseStartedAt, boolean sentSuccessfully) {
        MessageQueue current = messageQueueRepository.findByIdForUpdate(queueId).orElse(null);
        if (current == null) {
            return;
        }

        if (isCurrentLease(current, leaseStartedAt)) {
            if (sentSuccessfully) {
                current.sent();
            } else {
                current.failForRetry(MessageQueuePolicy.MAX_RETRY_COUNT);
            }
            messageQueueRepository.save(current);
            return;
        }

        log.warn("Skipped persisting send result because processing lease changed: queueId={}", queueId);
    }

    private boolean isCurrentLease(MessageQueue current, LocalDateTime leaseStartedAt) {
        return "PROCESSING".equals(current.getStatus())
                && Objects.equals(current.getProcessingStartedAt(), leaseStartedAt);
    }

}
