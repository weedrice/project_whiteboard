package com.weedrice.whiteboard.global.log.scheduler;

import com.weedrice.whiteboard.global.log.config.ErrorLogRetentionProperties;
import com.weedrice.whiteboard.global.log.service.ErrorLogCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(ErrorLogRetentionProperties.class)
public class ErrorLogCleanupScheduler {

    private final ErrorLogCleanupService cleanupService;
    private final ErrorLogRetentionProperties properties;
    private final Clock clock;

    @Scheduled(cron = "0 10 4 * * ?", zone = "Asia/Seoul")
    public void cleanupExpiredLogs() {
        LocalDateTime now = LocalDateTime.now(clock);
        int clientDeleted = deleteClientErrors(now.minusDays(properties.getClientRetentionDays()));
        int serverDeleted = deleteResolvedServerErrors(now.minusDays(properties.getResolvedRetentionDays()));
        if (clientDeleted > 0 || serverDeleted > 0) {
            log.info("Expired error logs deleted. client={}, resolvedServer={}", clientDeleted, serverDeleted);
        }
    }

    private int deleteClientErrors(LocalDateTime cutoff) {
        return deleteAllBatches(() -> cleanupService.deleteExpiredClientErrors(
                cutoff, properties.getCleanupBatchSize()));
    }

    private int deleteResolvedServerErrors(LocalDateTime cutoff) {
        return deleteAllBatches(() -> cleanupService.deleteExpiredResolvedServerErrors(
                cutoff, properties.getCleanupBatchSize()));
    }

    private int deleteAllBatches(BatchDeletion deletion) {
        int total = 0;
        int deleted;
        do {
            deleted = deletion.delete();
            total += deleted;
        } while (deleted == properties.getCleanupBatchSize());
        return total;
    }

    @FunctionalInterface
    private interface BatchDeletion {
        int delete();
    }
}
