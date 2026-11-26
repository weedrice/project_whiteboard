package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.repository.FileVariantRepository;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileVariantCleanupWorker {
    static final int RECONCILIATION_VERSION = 1;

    private static final int BATCH_SIZE = 100;
    private static final int PENDING_UPLOAD_GRACE_HOURS = 2;
    private static final int MAX_RETRY_COUNT = 10;
    private static final int MAX_RECONCILIATION_BACKOFF_FILES = 10_000;

    private final FileVariantRepository fileVariantRepository;
    private final FileVariantStateCommand stateCommand;
    private final FileUploadStateCommand uploadStateCommand;
    private final Clock clock;
    private final FileRepository fileRepository;
    private final FileImageVariantGenerator variantGenerator;
    private final AtomicLong reconciliationCursor = new AtomicLong();
    private final FileVariantCleanupMetrics metrics;
    // This bounded, process-local cache only reduces repeated work. Database eligibility
    // remains authoritative; restarts or eviction allow another attempt, never mark success.
    private final Map<Long, ReconciliationRetry> reconciliationRetries = new LinkedHashMap<>();

    public int cleanupStaleVariants() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusHours(PENDING_UPLOAD_GRACE_HOURS);
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> candidateIds = fileVariantRepository.findCleanupCandidateIds(
                cutoff,
                now,
                MAX_RETRY_COUNT,
                PageRequest.of(0, BATCH_SIZE));
        int deleted = 0;
        for (Long candidateId : candidateIds) {
            FileVariantStateCommand.FileVariantCleanupSnapshot snapshot = stateCommand.claimCleanup(
                    candidateId, cutoff, now, MAX_RETRY_COUNT);
            if (snapshot == null) {
                continue;
            }
            try {
                if (stateCommand.deleteClaimed(snapshot)) {
                    deleted++;
                }
            } catch (RuntimeException exception) {
                int retry = snapshot.retryCount() + 1;
                long delayMinutes = Math.min(60, 1L << Math.min(retry - 1, 6));
                boolean recorded = stateCommand.recordCleanupFailure(snapshot, exception.getClass().getSimpleName(),
                        now.plusMinutes(delayMinutes));
                if (recorded && retry < MAX_RETRY_COUNT) {
                    metrics.recordRetry();
                }
                log.warn("Failed to clean stale image variant. fileVariantId={}, exceptionType={}",
                        snapshot.fileVariantId(), exception.getClass().getSimpleName());
            }
        }
        metrics.update(
                fileVariantRepository.countCleanupDeadLetters(MAX_RETRY_COUNT),
                fileVariantRepository.findOldestCleanupDueAt(cutoff, MAX_RETRY_COUNT)
                        .map(value -> java.time.Duration.between(value, now).getSeconds()).orElse(0L));
        return deleted;
    }

    public synchronized int reconcileMissingVariants() {
        updateReconciliationBackoffMetric();
        var files = fileRepository.findActiveImagesMissingVariantsAfter(
                reconciliationCursor.get(), RECONCILIATION_VERSION, PageRequest.of(0, BATCH_SIZE));
        if (files.isEmpty()) {
            reconciliationCursor.set(0);
            return 0;
        }
        int attempted = 0;
        for (var file : files) {
            Long fileId = file.getFileId();
            ReconciliationRetry previousRetry = reconciliationRetries.get(fileId);
            if (previousRetry != null && clock.instant().isBefore(previousRetry.nextAttemptAt())) {
                metrics.recordReconciliationDeferred();
                reconciliationCursor.set(fileId);
                continue;
            }
            try {
                FileImageVariantGenerator.ReconciliationResult result =
                        variantGenerator.generateMissingVariants(file);
                uploadStateCommand.markVariantReconciled(
                        file.getFileId(),
                        result.width(),
                        result.height(),
                        result.expectedVariantCount(),
                        RECONCILIATION_VERSION);
                attempted += result.generated();
                reconciliationRetries.remove(fileId);
                metrics.recordReconciliationSuccess();
            } catch (RuntimeException exception) {
                int failures = previousRetry == null ? 1 : Math.min(previousRetry.failures() + 1, 6);
                long delayHours = Math.min(24, 1L << (failures - 1));
                Instant nextAttemptAt = clock.instant().plus(Duration.ofHours(delayHours));
                if (previousRetry == null && reconciliationRetries.size() >= MAX_RECONCILIATION_BACKOFF_FILES) {
                    reconciliationRetries.remove(reconciliationRetries.keySet().iterator().next());
                }
                reconciliationRetries.put(fileId, new ReconciliationRetry(failures, nextAttemptAt));
                metrics.recordReconciliationFailure();
                String errorCode = exception instanceof BusinessException businessException
                        ? businessException.getErrorCode().name() : "UNEXPECTED";
                log.warn("Failed to reconcile image variants. fileId={}, exceptionType={}, errorCode={}, nextAttemptAt={}",
                        fileId, exception.getClass().getSimpleName(), errorCode, nextAttemptAt);
            }
            reconciliationCursor.set(fileId);
        }
        updateReconciliationBackoffMetric();
        return attempted;
    }

    private void updateReconciliationBackoffMetric() {
        Instant now = clock.instant();
        metrics.updateReconciliationBackoffFiles(reconciliationRetries.values().stream()
                .filter(retry -> now.isBefore(retry.nextAttemptAt())).count());
    }

    private record ReconciliationRetry(int failures, Instant nextAttemptAt) {
    }
}
