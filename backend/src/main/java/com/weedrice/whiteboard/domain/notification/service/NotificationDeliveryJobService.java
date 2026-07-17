package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob;
import com.weedrice.whiteboard.domain.notification.repository.NotificationDeliveryJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class NotificationDeliveryJobService {

    private final NotificationDeliveryJobRepository jobRepository;
    private final NotificationDeliveryJobKickoff kickoff;
    private final Clock clock;
    private final NotificationPayloadValidator payloadValidator;

    @Transactional
    public NotificationDeliveryJob enqueue(NotificationEvent event) {
        if (event == null || event.getUserToNotify() == null || event.getUserToNotify().getUserId() == null
                || event.getNotificationType() == null || event.getSourceType() == null) {
            return null;
        }
        NotificationDeliveryJob existing = jobRepository.findByEventId(event.getEventId()).orElse(null);
        if (existing != null) {
            return existing;
        }
        NotificationDeliveryJob saved = jobRepository.save(
                NotificationDeliveryJob.from(event, LocalDateTime.now(clock)));
        String invalidReason = payloadValidator.validate(event);
        if (invalidReason != null) {
            saved.rejectInvalidPayload(invalidReason, LocalDateTime.now(clock));
            return saved;
        }
        scheduleAfterCommit(saved.getJobId());
        return saved;
    }

    private void scheduleAfterCommit(Long jobId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            kickoff.process(jobId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                kickoff.process(jobId);
            }
        });
    }

}
