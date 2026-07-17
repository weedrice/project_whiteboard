package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob;
import com.weedrice.whiteboard.domain.notification.repository.PushDeliveryJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class PushDeliveryJobProcessor {

    private static final int BATCH_SIZE = 20;
    private static final int LEASE_MINUTES = 5;
    private static final int MAX_BACKOFF_MINUTES = 60;
    private static final int TERMINAL_RETENTION_DAYS = 7;
    private static final int FAILED_RETENTION_DAYS = 30;

    private final PushDeliveryJobRepository jobRepository;
    private final PushDeliveryJobTransaction jobTransaction;
    private final PushDispatchSnapshotReader snapshotReader;
    private final PushNotificationDispatcher dispatcher;
    private final PushDeliveryJobMetrics metrics;
    private final Clock clock;
    private final AtomicLong lastCleanupEpochHour = new AtomicLong(Long.MIN_VALUE);

    public int processDueJobs() {
        recoverStaleJobs();
        cleanupTerminalJobsOncePerHour();
        LocalDateTime now = now();
        List<Long> jobIds = jobRepository.findDueJobIds(now, PageRequest.of(0, BATCH_SIZE));
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
        PushDeliveryLease lease = jobTransaction.claim(jobId, now());
        if (lease == null) {
            return false;
        }
        try {
            if (!snapshotReader.isCurrentAndEnabled(lease.subscription())) {
                jobTransaction.expire(lease, "Subscription changed or push disabled", false);
                metrics.recordOutcome("stale_subscription");
                return true;
            }
            PushDeliveryOutcome outcome = dispatcher.send(lease.subscription(), lease.payload());
            switch (outcome) {
                case SUCCESS -> {
                    jobTransaction.complete(lease);
                    metrics.recordOutcome("success");
                    return true;
                }
                case EXPIRED -> {
                    jobTransaction.expire(lease, "Push provider expired subscription", true);
                    metrics.recordOutcome("expired");
                    return true;
                }
                case PERMANENT_FAILURE -> {
                    jobTransaction.failPermanently(lease, "Non-retryable push response", now());
                    metrics.recordOutcome("dead_letter");
                    return false;
                }
                case RETRYABLE_FAILURE -> {
                    retry(lease, "Retryable push response");
                    return false;
                }
                default -> throw new IllegalStateException("Unknown push delivery outcome: " + outcome);
            }
        } catch (RuntimeException exception) {
            retry(lease, exception.getClass().getSimpleName());
            log.warn("Web push delivery job failed. jobId={}, exceptionType={}",
                    jobId, exception.getClass().getSimpleName());
            return false;
        }
    }

    private void retry(PushDeliveryLease lease, String error) {
        int retryNumber = currentRetryCount(lease.jobId()) + 1;
        boolean deadLettered = jobTransaction.retry(
                lease,
                error,
                now(),
                now().plusMinutes(backoffMinutes(retryNumber)));
        metrics.recordOutcome(deadLettered ? "dead_letter" : "retry");
    }

    private void recoverStaleJobs() {
        LocalDateTime now = now();
        LocalDateTime staleBefore = now.minusMinutes(LEASE_MINUTES);
        for (Long jobId : jobRepository.findStaleProcessingJobIds(staleBefore, PageRequest.of(0, BATCH_SIZE))) {
            int retryNumber = currentRetryCount(jobId) + 1;
            boolean deadLettered = jobTransaction.recoverStale(
                    jobId,
                    staleBefore,
                    now,
                    now.plusMinutes(backoffMinutes(retryNumber)));
            metrics.recordOutcome(deadLettered ? "dead_letter" : "lease_recovered");
        }
    }

    private void cleanupTerminalJobsOncePerHour() {
        long currentHour = clock.instant().getEpochSecond() / 3_600;
        long previousHour = lastCleanupEpochHour.get();
        if (previousHour == currentHour || !lastCleanupEpochHour.compareAndSet(previousHour, currentHour)) {
            return;
        }
        try {
            jobTransaction.deleteTerminalBefore(now().minusDays(TERMINAL_RETENTION_DAYS));
            jobTransaction.deleteFailedBefore(now().minusDays(FAILED_RETENTION_DAYS));
        } catch (RuntimeException exception) {
            metrics.recordOutcome("cleanup_failure");
            log.warn("Failed to clean terminal push delivery jobs. exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }

    private void recordBacklog() {
        LocalDateTime now = now();
        long oldestAge = jobRepository.findOldestPendingAttemptAt()
                .map(value -> Math.max(0, Duration.between(value, now).getSeconds()))
                .orElse(0L);
        metrics.updateBacklog(
                jobRepository.countByStatus(PushDeliveryJob.Status.PENDING),
                jobRepository.countByStatus(PushDeliveryJob.Status.FAILED),
                oldestAge);
    }

    private int currentRetryCount(Long jobId) {
        return jobRepository.findById(jobId).map(PushDeliveryJob::getRetryCount).orElse(0);
    }

    private long backoffMinutes(int retryNumber) {
        long backoff = 1L << Math.min(Math.max(retryNumber - 1, 0), 6);
        return Math.min(backoff, MAX_BACKOFF_MINUTES);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
