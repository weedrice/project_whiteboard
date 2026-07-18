package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob;
import com.weedrice.whiteboard.domain.notification.config.NotificationDeliveryJobProperties;
import com.weedrice.whiteboard.domain.notification.repository.NotificationDeliveryJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class NotificationDeliveryJobProcessor {

    private static final int LEASE_MINUTES = 5;
    private static final int MAX_BACKOFF_MINUTES = 60;

    private final NotificationDeliveryJobRepository jobRepository;
    private final NotificationDeliveryJobTransaction jobTransaction;
    private final NotificationDeliveryJobMetrics metrics;
    private final NotificationDeliveryJobProperties properties;
    private final Clock clock;
    private final AtomicLong lastCleanupEpochHour = new AtomicLong(Long.MIN_VALUE);

    public int processDueJobs() {
        recoverStaleJobs();
        cleanupCompletedJobsOncePerHour();
        LocalDateTime now = now();
        List<Long> jobIds = jobRepository.findDueJobIds(
                now, PageRequest.of(0, properties.getProcessingBatchSize()));
        int processed = 0;
        for (Long jobId : jobIds) {
            if (processJob(jobId)) {
                processed++;
            }
        }
        recordBacklog();
        return processed;
    }

    public boolean processJob(Long jobId) {
        LocalDateTime claimedAt = now();
        LocalDateTime lease = jobTransaction.claim(jobId, claimedAt);
        if (lease == null) {
            return false;
        }
        try {
            DeliveryJobTransitionResult result = jobTransaction.deliver(jobId, lease);
            return recordDeliveryResult(jobId, result);
        } catch (RuntimeException exception) {
            int retryNumber = currentRetryCount(jobId) + 1;
            LocalDateTime nextAttempt = now().plusMinutes(backoffMinutes(retryNumber));
            DeliveryJobTransitionResult result = jobTransaction.fail(
                    jobId,
                    lease,
                    exception.getClass().getSimpleName(),
                    nextAttempt);
            recordFailureResult(result);
            log.warn("Notification delivery job failed. jobId={}, transitionResult={}, exceptionType={}",
                    jobId, result, exception.getClass().getSimpleName());
            return false;
        }
    }

    private boolean recordDeliveryResult(Long jobId, DeliveryJobTransitionResult result) {
        return switch (result) {
            case APPLIED_SUCCESS -> {
                metrics.recordOutcome("success");
                yield true;
            }
            case REJECTED_INVALID -> {
                metrics.recordOutcome("invalid_payload");
                log.warn("Notification delivery job rejected an invalid payload. jobId={}", jobId);
                yield false;
            }
            case LEASE_LOST -> {
                metrics.recordOutcome("lease_lost");
                log.warn("Notification delivery job lease was lost before delivery. jobId={}", jobId);
                yield false;
            }
            default -> {
                metrics.recordOutcome("transition_invalid");
                log.error("Notification delivery returned an unexpected transition. jobId={}, transitionResult={}",
                        jobId, result);
                yield false;
            }
        };
    }

    private void recordFailureResult(DeliveryJobTransitionResult result) {
        switch (result) {
            case APPLIED_RETRY -> metrics.recordOutcome("retry");
            case APPLIED_DEAD_LETTER -> metrics.recordOutcome("dead_letter");
            case LEASE_LOST -> metrics.recordOutcome("lease_lost");
            default -> metrics.recordOutcome("transition_invalid");
        }
    }

    private void recoverStaleJobs() {
        LocalDateTime now = now();
        LocalDateTime staleBefore = now.minusMinutes(LEASE_MINUTES);
        List<Long> staleIds = jobRepository.findStaleProcessingJobIds(
                staleBefore,
                PageRequest.of(0, properties.getProcessingBatchSize()));
        for (Long staleId : staleIds) {
            int retryNumber = currentRetryCount(staleId) + 1;
            DeliveryJobTransitionResult result = jobTransaction.recoverStale(
                    staleId,
                    staleBefore,
                    now.plusMinutes(backoffMinutes(retryNumber)));
            switch (result) {
                case APPLIED_RETRY -> metrics.recordOutcome("lease_recovered");
                case APPLIED_DEAD_LETTER -> metrics.recordOutcome("dead_letter");
                case LEASE_LOST -> metrics.recordOutcome("lease_lost");
                default -> metrics.recordOutcome("transition_invalid");
            }
        }
    }

    private void cleanupCompletedJobsOncePerHour() {
        long currentHour = clock.instant().getEpochSecond() / 3_600;
        long previousHour = lastCleanupEpochHour.get();
        if (previousHour == currentHour || !lastCleanupEpochHour.compareAndSet(previousHour, currentHour)) {
            return;
        }
        try {
            int deleted = deleteInBoundedBatches(
                    true, now().minusDays(properties.getCompletedRetentionDays()));
            deleted += deleteInBoundedBatches(
                    false, now().minusDays(properties.getFailedRetentionDays()));
            if (deleted > 0) {
                log.info("Deleted {} terminal notification delivery job(s)", deleted);
            }
        } catch (RuntimeException exception) {
            lastCleanupEpochHour.compareAndSet(currentHour, previousHour);
            metrics.recordOutcome("cleanup_failure");
            log.warn("Failed to clean terminal notification delivery jobs. exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }

    private int deleteInBoundedBatches(boolean completed, LocalDateTime cutoff) {
        int totalDeleted = 0;
        for (int batch = 0; batch < properties.getCleanupMaxBatches(); batch++) {
            int deleted = completed
                    ? jobTransaction.deleteCompletedBefore(cutoff, properties.getCleanupBatchSize())
                    : jobTransaction.deleteFailedBefore(cutoff, properties.getCleanupBatchSize());
            totalDeleted += deleted;
            if (deleted < properties.getCleanupBatchSize()) {
                break;
            }
        }
        return totalDeleted;
    }

    private int currentRetryCount(Long jobId) {
        return jobRepository.findById(jobId)
                .map(NotificationDeliveryJob::getRetryCount)
                .orElse(0);
    }

    private long backoffMinutes(int retryNumber) {
        long backoff = 1L << Math.min(Math.max(retryNumber - 1, 0), 6);
        return Math.min(backoff, MAX_BACKOFF_MINUTES);
    }

    private void recordBacklog() {
        metrics.updateBacklog(
                jobRepository.countByStatus(NotificationDeliveryJob.Status.PENDING),
                jobRepository.countByStatus(NotificationDeliveryJob.Status.FAILED));
        LocalDateTime now = now();
        metrics.updateAges(
                jobRepository.findOldestPendingAttemptAt()
                        .map(value -> java.time.Duration.between(value, now).getSeconds()).orElse(0L),
                jobRepository.findOldestProcessingStartedAt()
                        .map(value -> java.time.Duration.between(value, now).getSeconds()).orElse(0L));
    }

    public boolean redriveFailedJob(Long jobId) {
        boolean redriven = jobTransaction.redriveFailed(jobId, now());
        if (redriven) {
            metrics.recordOutcome("redrive");
        }
        return redriven;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
