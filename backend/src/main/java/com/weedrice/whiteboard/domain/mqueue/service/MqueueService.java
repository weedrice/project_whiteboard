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

import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqueueService {
    private static final String EMAIL_SUBJECT = "[noviIs] Notification";

    private final MessageQueueRepository messageQueueRepository;
    private final EmailService emailService;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

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
    public void sendEmail(Long queueId) {
        MessageQueue message = messageQueueRepository.findByIdWithTargetUser(queueId).orElse(null);
        if (message == null || !"PROCESSING".equals(message.getStatus())) {
            return;
        }

        LocalDateTime leaseStartedAt = message.getProcessingStartedAt();
        boolean sentSuccessfully = false;

        try {
            log.info("Email sending attempt: {} - {}", message.getTargetUser().getEmail(), message.getContent());
            emailService.sendEmail(message.getTargetUser().getEmail(), EMAIL_SUBJECT, message.getContent());
            sentSuccessfully = true;
            log.info("Email sent successfully: queueId={}", queueId);
        } catch (Exception e) {
            log.error("Email sending failed: queueId={}", queueId, e);
        }

        boolean finalSentSuccessfully = sentSuccessfully;
        transactionTemplate.executeWithoutResult(status ->
                persistSendResult(queueId, leaseStartedAt, finalSentSuccessfully));
    }

    @Transactional
    public void recoverRejectedDispatch(Long queueId) {
        MessageQueue message = messageQueueRepository.findByIdForUpdate(queueId).orElse(null);
        if (message == null || !"PROCESSING".equals(message.getStatus())) {
            return;
        }
        message.releaseProcessingLease();
        messageQueueRepository.save(message);
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

        if (sentSuccessfully && canFinalizeRecoveredMessage(current)) {
            log.warn("Finalizing recovered message as sent after lease changed: queueId={}", queueId);
            current.sent();
            messageQueueRepository.save(current);
            return;
        }

        log.warn("Skipped persisting send result because processing lease changed: queueId={}", queueId);
    }

    private boolean isCurrentLease(MessageQueue current, LocalDateTime leaseStartedAt) {
        return "PROCESSING".equals(current.getStatus())
                && Objects.equals(current.getProcessingStartedAt(), leaseStartedAt);
    }

    private boolean canFinalizeRecoveredMessage(MessageQueue current) {
        return current.getProcessingStartedAt() == null
                && ("PENDING".equals(current.getStatus()) || "FAILED".equals(current.getStatus()));
    }
}
