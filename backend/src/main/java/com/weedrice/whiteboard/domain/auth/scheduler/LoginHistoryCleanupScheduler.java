package com.weedrice.whiteboard.domain.auth.scheduler;

import com.weedrice.whiteboard.domain.auth.LoginHistoryRetentionProperties;
import com.weedrice.whiteboard.domain.auth.service.LoginHistoryCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginHistoryCleanupScheduler {

    private final LoginHistoryCleanupService cleanupService;
    private final LoginHistoryRetentionProperties properties;

    @Scheduled(cron = "0 40 3 * * ?")
    public void cleanupExpiredLoginHistories() {
        int totalDeleted = 0;
        for (int batch = 0; batch < properties.getCleanupMaxBatches(); batch++) {
            int deleted = cleanupService.deleteExpiredBatch();
            totalDeleted += deleted;
            if (deleted < properties.getCleanupBatchSize()) {
                break;
            }
        }
        if (totalDeleted > 0) {
            log.info("Deleted {} expired login history row(s)", totalDeleted);
        }
    }
}
