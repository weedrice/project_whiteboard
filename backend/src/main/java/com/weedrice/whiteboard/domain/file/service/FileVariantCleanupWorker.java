package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.repository.FileVariantRepository;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileVariantCleanupWorker {

    private static final int BATCH_SIZE = 100;
    private static final int PENDING_UPLOAD_GRACE_HOURS = 2;
    private static final int MAX_RETRY_COUNT = 10;

    private final FileVariantRepository fileVariantRepository;
    private final FileVariantStateCommand stateCommand;
    private final FileStorageService fileStorageService;
    private final Clock clock;
    private final FileRepository fileRepository;
    private final FileImageVariantGenerator variantGenerator;
    private final AtomicLong reconciliationCursor = new AtomicLong();
    private final FileVariantCleanupMetrics metrics;

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
                fileStorageService.deleteFileOrThrow(snapshot.filePath());
                stateCommand.deleteClaimed(snapshot.fileVariantId());
                deleted++;
            } catch (RuntimeException exception) {
                int retry = snapshot.retryCount() + 1;
                long delayMinutes = Math.min(60, 1L << Math.min(retry - 1, 6));
                stateCommand.recordCleanupFailure(candidateId, exception.getClass().getSimpleName(),
                        now.plusMinutes(delayMinutes));
                metrics.recordRetry();
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

    public int reconcileMissingVariants() {
        var files = fileRepository.findActiveImagesMissingVariantsAfter(
                reconciliationCursor.get(), PageRequest.of(0, BATCH_SIZE));
        if (files.isEmpty()) {
            reconciliationCursor.set(0);
            return 0;
        }
        int attempted = 0;
        for (var file : files) {
            try {
                attempted += variantGenerator.generateMissingVariants(file);
            } catch (RuntimeException exception) {
                log.warn("Failed to reconcile image variants. fileId={}, exceptionType={}",
                        file.getFileId(), exception.getClass().getSimpleName());
            }
            reconciliationCursor.set(file.getFileId());
        }
        return attempted;
    }
}
