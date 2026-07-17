package com.weedrice.whiteboard.domain.auth.scheduler;

import com.weedrice.whiteboard.domain.auth.VerificationCodeRetentionProperties;
import com.weedrice.whiteboard.domain.auth.service.VerificationCodeCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationCodeCleanupScheduler {

    private final VerificationCodeCleanupService cleanupService;
    private final VerificationCodeRetentionProperties properties;

    @Scheduled(cron = "0 20 4 * * ?", zone = "Asia/Seoul")
    public void cleanupExpiredVerificationCodes() {
        int recovered = recoverStalePendingBatches();
        int deletedPasswordResetTokens = deleteExpiredPasswordResetTokenBatches();
        int totalDeleted = 0;
        int deleted;
        do {
            deleted = cleanupService.deleteExpiredTerminalBatch();
            totalDeleted += deleted;
        } while (deleted == properties.getCleanupBatchSize());

        if (recovered > 0 || deletedPasswordResetTokens > 0 || totalDeleted > 0) {
            log.info("Verification code retention completed. recoveredPending={}, deletedPasswordResetTokens={}, deletedTerminal={}",
                    recovered,
                    deletedPasswordResetTokens,
                    totalDeleted);
        }
    }

    private int deleteExpiredPasswordResetTokenBatches() {
        int totalDeleted = 0;
        int deleted;
        do {
            deleted = cleanupService.deleteExpiredPasswordResetTokenBatch();
            totalDeleted += deleted;
        } while (deleted == properties.getPasswordResetTokenCleanupBatchSize());
        return totalDeleted;
    }

    private int recoverStalePendingBatches() {
        int totalRecovered = 0;
        for (int batch = 0; batch < properties.getPendingRecoveryMaxBatches(); batch++) {
            int recovered = cleanupService.recoverStalePendingDeliveries();
            totalRecovered += recovered;
            if (recovered < properties.getPendingRecoveryBatchSize()) {
                break;
            }
        }
        return totalRecovered;
    }
}
