package com.weedrice.whiteboard.domain.auth.scheduler;

import com.weedrice.whiteboard.domain.auth.service.RefreshTokenCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private static final int MAX_BATCHES_PER_RUN = 20;

    private final RefreshTokenCleanupService cleanupService;

    @Scheduled(cron = "0 40 4 * * ?", zone = "Asia/Seoul")
    public void cleanupExpiredRefreshTokens() {
        int totalDeleted = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int deleted = cleanupService.deleteExpiredBatch();
            totalDeleted += deleted;
            if (deleted < RefreshTokenCleanupService.CLEANUP_BATCH_SIZE) {
                break;
            }
        }
        if (totalDeleted > 0) {
            log.info("Refresh token retention completed. deleted={}", totalDeleted);
        }
    }
}
