package com.weedrice.whiteboard.domain.mqueue.service;

import com.weedrice.whiteboard.domain.mqueue.entity.MessageQueue;
import com.weedrice.whiteboard.domain.mqueue.repository.MessageQueueRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqueueService {
    private static final int MAX_RETRY_COUNT = 5;
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

        try {
            log.info("Email sending attempt: {} - {}", message.getTargetUser().getEmail(), message.getContent());
            emailService.sendEmail(message.getTargetUser().getEmail(), EMAIL_SUBJECT, message.getContent());
            message.sent();
            log.info("Email sent successfully: queueId={}", queueId);
        } catch (Exception e) {
            log.error("Email sending failed: queueId={}", queueId, e);
            message.failForRetry(MAX_RETRY_COUNT);
        }

        transactionTemplate.executeWithoutResult(status -> messageQueueRepository.save(message));
    }
}
