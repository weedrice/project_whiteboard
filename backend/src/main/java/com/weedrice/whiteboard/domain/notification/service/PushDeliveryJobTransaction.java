package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob;
import com.weedrice.whiteboard.domain.notification.repository.PushDeliveryJobRepository;
import com.weedrice.whiteboard.domain.notification.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class PushDeliveryJobTransaction {

    static final int MAX_RETRY_COUNT = 5;

    private final PushDeliveryJobRepository jobRepository;
    private final PushSubscriptionRepository subscriptionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PushDeliveryLease claim(Long jobId, LocalDateTime claimedAt) {
        return jobRepository.findByIdForUpdate(jobId)
                .filter(job -> job.isDue(claimedAt))
                .map(job -> {
                    job.claim(claimedAt);
                    PushSubscriptionSnapshot snapshot = new PushSubscriptionSnapshot(
                            job.getSubscriptionId(),
                            job.getReceiverUserId(),
                            job.getEndpoint(),
                            job.getP256dh(),
                            job.getAuth(),
                            job.getSubscriptionModifiedAt());
                    return new PushDeliveryLease(jobId, claimedAt, snapshot, job.getPayload());
                })
                .orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(PushDeliveryLease lease) {
        return withLease(lease, PushDeliveryJob::complete);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expire(PushDeliveryLease lease, String reason, boolean deleteSubscription) {
        return withLease(lease, job -> {
            job.expire(reason);
            if (deleteSubscription) {
                PushSubscriptionSnapshot snapshot = lease.subscription();
                subscriptionRepository.deleteIfSnapshotMatches(
                        snapshot.subscriptionId(),
                        snapshot.userId(),
                        snapshot.endpoint(),
                        snapshot.p256dh(),
                        snapshot.auth(),
                        snapshot.modifiedAt());
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean retry(PushDeliveryLease lease, String error, LocalDateTime failedAt, LocalDateTime nextAttempt) {
        return jobRepository.findByIdForUpdate(lease.jobId())
                .filter(job -> job.hasLease(lease.claimedAt()))
                .map(job -> job.retry(error, failedAt, nextAttempt, MAX_RETRY_COUNT))
                .orElse(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean failPermanently(PushDeliveryLease lease, String error, LocalDateTime failedAt) {
        return withLease(lease, job -> job.failPermanently(error, failedAt));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recoverStale(Long jobId, LocalDateTime staleBefore, LocalDateTime failedAt,
            LocalDateTime nextAttempt) {
        return jobRepository.findByIdForUpdate(jobId)
                .filter(job -> job.getStatus() == PushDeliveryJob.Status.PROCESSING)
                .filter(job -> job.getProcessingStartedAt() == null
                        || job.getProcessingStartedAt().isBefore(staleBefore))
                .map(job -> job.retry("Processing lease expired", failedAt, nextAttempt, MAX_RETRY_COUNT))
                .orElse(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteTerminalBefore(LocalDateTime cutoff) {
        return jobRepository.deleteTerminalBefore(cutoff);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteFailedBefore(LocalDateTime cutoff) {
        return jobRepository.deleteFailedBefore(cutoff);
    }

    private boolean withLease(PushDeliveryLease lease, java.util.function.Consumer<PushDeliveryJob> action) {
        return jobRepository.findByIdForUpdate(lease.jobId())
                .filter(job -> job.hasLease(lease.claimedAt()))
                .map(job -> {
                    action.accept(job);
                    return true;
                })
                .orElse(false);
    }
}
