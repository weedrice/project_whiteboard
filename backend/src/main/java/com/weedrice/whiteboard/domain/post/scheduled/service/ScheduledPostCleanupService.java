package com.weedrice.whiteboard.domain.post.scheduled.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledPostCleanupService {

    static final int FAILED_RETENTION_DAYS = 30;
    private static final int CLEANUP_BATCH_SIZE = 100;

    private final ScheduledPostCleanupBatchService cleanupBatchService;
    private final Clock clock;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int cleanupExpiredFailedPosts() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime cutoff = now.minusDays(FAILED_RETENTION_DAYS);
        int cleanedCount = 0;
        while (true) {
            int batchCount = cleanupBatchService.cleanupExpiredFailedBatch(cutoff, now, CLEANUP_BATCH_SIZE);
            cleanedCount += batchCount;
            if (batchCount < CLEANUP_BATCH_SIZE) {
                break;
            }
        }
        if (cleanedCount > 0) {
            log.info("Released {} expired failed scheduled post draft(s)", cleanedCount);
        }
        return cleanedCount;
    }
}
