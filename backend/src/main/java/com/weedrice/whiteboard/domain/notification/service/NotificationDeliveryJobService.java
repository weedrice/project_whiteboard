package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob;
import com.weedrice.whiteboard.domain.notification.repository.NotificationDeliveryJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
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
        LocalDateTime now = LocalDateTime.now(clock);
        NotificationDeliveryJob candidate = NotificationDeliveryJob.from(event, now);
        String invalidReason = payloadValidator.validate(event);
        if (invalidReason != null) {
            candidate.rejectInvalidPayload(invalidReason, now);
        }
        int inserted = jobRepository.insertIfAbsent(candidate, now);
        NotificationDeliveryJob saved = jobRepository.findByEventId(event.getEventId())
                .orElseThrow(() -> new IllegalStateException("Notification delivery job insert was not visible"));
        if (inserted == 1 && invalidReason == null
                && saved.getStatus() == NotificationDeliveryJob.Status.PENDING) {
            scheduleAfterCommit(saved.getJobId());
        }
        return saved;
    }

    private void scheduleAfterCommit(Long jobId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            submitKickoff(jobId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submitKickoff(jobId);
            }
        });
    }

    private void submitKickoff(Long jobId) {
        try {
            kickoff.process(jobId);
        } catch (RejectedExecutionException exception) {
            log.warn("Notification delivery kickoff rejected; scheduler will retry. jobId={}", jobId);
        }
    }

}
