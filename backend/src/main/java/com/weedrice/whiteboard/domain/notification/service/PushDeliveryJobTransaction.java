package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob;
import com.weedrice.whiteboard.domain.notification.repository.PushDeliveryJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class PushDeliveryJobTransaction {

    static final int MAX_RETRY_COUNT = 5;

    private final PushDeliveryJobRepository jobRepository;

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
    public DeliveryJobTransitionResult complete(PushDeliveryLease lease) {
        return withLease(lease, PushDeliveryJob::complete, DeliveryJobTransitionResult.APPLIED_SUCCESS);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeliveryJobTransitionResult expire(PushDeliveryLease lease, String reason) {
        return withLease(lease, job -> job.expire(reason), DeliveryJobTransitionResult.APPLIED_SUCCESS);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeliveryJobTransitionResult retry(
            PushDeliveryLease lease,
            String error,
            LocalDateTime failedAt,
            LocalDateTime nextAttempt) {
        return jobRepository.findByIdForUpdate(lease.jobId())
                .filter(job -> job.hasLease(lease.claimedAt()))
                .map(job -> job.retry(error, failedAt, nextAttempt, MAX_RETRY_COUNT)
                        ? DeliveryJobTransitionResult.APPLIED_DEAD_LETTER
                        : DeliveryJobTransitionResult.APPLIED_RETRY)
                .orElse(DeliveryJobTransitionResult.LEASE_LOST);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeliveryJobTransitionResult failPermanently(
            PushDeliveryLease lease,
            String error,
            LocalDateTime failedAt) {
        return withLease(
                lease,
                job -> job.failPermanently(error, failedAt),
                DeliveryJobTransitionResult.APPLIED_DEAD_LETTER);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeliveryJobTransitionResult recoverStale(Long jobId, LocalDateTime staleBefore, LocalDateTime failedAt,
            LocalDateTime nextAttempt) {
        return jobRepository.findByIdForUpdate(jobId)
                .filter(job -> job.getStatus() == PushDeliveryJob.Status.PROCESSING)
                .filter(job -> job.getProcessingStartedAt() == null
                        || job.getProcessingStartedAt().isBefore(staleBefore))
                .map(job -> job.retry("Processing lease expired", failedAt, nextAttempt, MAX_RETRY_COUNT)
                        ? DeliveryJobTransitionResult.APPLIED_DEAD_LETTER
                        : DeliveryJobTransitionResult.APPLIED_RETRY)
                .orElse(DeliveryJobTransitionResult.LEASE_LOST);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteTerminalBefore(LocalDateTime cutoff, int batchSize) {
        var ids = jobRepository.findTerminalIdsBefore(cutoff, PageRequest.of(0, batchSize));
        jobRepository.deleteAllByIdInBatch(ids);
        return ids.size();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteFailedBefore(LocalDateTime cutoff, int batchSize) {
        var ids = jobRepository.findFailedIdsBefore(cutoff, PageRequest.of(0, batchSize));
        jobRepository.deleteAllByIdInBatch(ids);
        return ids.size();
    }

    private DeliveryJobTransitionResult withLease(
            PushDeliveryLease lease,
            java.util.function.Consumer<PushDeliveryJob> action,
            DeliveryJobTransitionResult appliedResult) {
        return jobRepository.findByIdForUpdate(lease.jobId())
                .filter(job -> job.hasLease(lease.claimedAt()))
                .map(job -> {
                    action.accept(job);
                    return appliedResult;
                })
                .orElse(DeliveryJobTransitionResult.LEASE_LOST);
    }
}
