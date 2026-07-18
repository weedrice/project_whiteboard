package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.dto.NotificationMessageParamsCodec;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob;
import com.weedrice.whiteboard.domain.notification.repository.NotificationDeliveryJobRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class NotificationDeliveryJobTransaction {

    static final int MAX_RETRY_COUNT = 5;

    private final NotificationDeliveryJobRepository jobRepository;
    private final NotificationCommandService notificationCommandService;
    private final NotificationDeliveryPublisher deliveryPublisher;
    private final EntityManager entityManager;
    private final NotificationPayloadValidator payloadValidator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LocalDateTime claim(Long jobId, LocalDateTime claimedAt) {
        return jobRepository.findByIdForUpdate(jobId)
                .filter(job -> job.isDue(claimedAt))
                .map(job -> {
                    job.claim(claimedAt);
                    return claimedAt;
                })
                .orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeliveryJobTransitionResult deliver(Long jobId, LocalDateTime claimedAt) {
        NotificationDeliveryJob job = jobRepository.findByIdForUpdate(jobId)
                .filter(candidate -> candidate.hasLease(claimedAt))
                .orElse(null);
        if (job == null) {
            return DeliveryJobTransitionResult.LEASE_LOST;
        }
        String invalidReason = payloadValidator.validate(job);
        if (invalidReason != null) {
            job.rejectInvalidPayload(invalidReason, LocalDateTime.now());
            return DeliveryJobTransitionResult.REJECTED_INVALID;
        }
        Notification notification = notificationCommandService.handleNotificationEvent(toEvent(job));
        job.complete();
        if (notification != null) {
            deliveryPublisher.publishAfterCommit(job.getReceiverUserId(), notification);
        }
        return DeliveryJobTransitionResult.APPLIED_SUCCESS;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeliveryJobTransitionResult fail(
            Long jobId,
            LocalDateTime claimedAt,
            String error,
            LocalDateTime nextAttemptAt) {
        return jobRepository.findByIdForUpdate(jobId)
                .filter(job -> job.hasLease(claimedAt))
                .map(job -> job.fail(error, LocalDateTime.now(), nextAttemptAt, MAX_RETRY_COUNT)
                        ? DeliveryJobTransitionResult.APPLIED_DEAD_LETTER
                        : DeliveryJobTransitionResult.APPLIED_RETRY)
                .orElse(DeliveryJobTransitionResult.LEASE_LOST);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeliveryJobTransitionResult recoverStale(
            Long jobId,
            LocalDateTime staleBefore,
            LocalDateTime nextAttemptAt) {
        return jobRepository.findByIdForUpdate(jobId)
                .filter(job -> job.getStatus() == NotificationDeliveryJob.Status.PROCESSING)
                .filter(job -> job.getProcessingStartedAt() == null
                        || job.getProcessingStartedAt().isBefore(staleBefore))
                .map(job -> job.fail("Processing lease expired", LocalDateTime.now(), nextAttemptAt, MAX_RETRY_COUNT)
                        ? DeliveryJobTransitionResult.APPLIED_DEAD_LETTER
                        : DeliveryJobTransitionResult.APPLIED_RETRY)
                .orElse(DeliveryJobTransitionResult.LEASE_LOST);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteCompletedBefore(LocalDateTime cutoff, int batchSize) {
        return jobRepository.deleteCompletedBatch(cutoff, batchSize);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteFailedBefore(LocalDateTime cutoff, int batchSize) {
        return jobRepository.deleteFailedBatch(cutoff, batchSize);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean redriveFailed(Long jobId, LocalDateTime now) {
        return jobRepository.findByIdForUpdate(jobId)
                .map(job -> job.redrive(now))
                .orElse(false);
    }

    private NotificationEvent toEvent(NotificationDeliveryJob job) {
        User receiver = entityManager.getReference(User.class, job.getReceiverUserId());
        User actor = job.getActorUserId() == null
                ? null
                : entityManager.getReference(User.class, job.getActorUserId());
        Agent actorAgent = job.getActorAgentId() == null
                ? null
                : entityManager.getReference(Agent.class, job.getActorAgentId());
        if (job.getMessageKey() != null && !job.getMessageKey().isBlank()) {
            return NotificationEvent.restored(
                    job.getEventId(),
                    receiver,
                    actor,
                    actorAgent,
                    job.getNotificationType(),
                    job.getSourceType(),
                    job.getSourceId(),
                    null,
                    job.getMessageKey(),
                    NotificationMessageParamsCodec.decode(job.getMessageParams()));
        }
        return NotificationEvent.restored(
                job.getEventId(),
                receiver,
                actor,
                actorAgent,
                job.getNotificationType(),
                job.getSourceType(),
                job.getSourceId(),
                job.getContent(),
                null,
                java.util.List.of());
    }
}
