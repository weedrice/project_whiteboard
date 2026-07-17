package com.weedrice.whiteboard.domain.auth.scheduler;

import com.weedrice.whiteboard.domain.auth.VerificationCodeRetentionProperties;
import com.weedrice.whiteboard.domain.auth.service.VerificationCodeCleanupService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationCodeCleanupSchedulerTest {

    @Test
    void cleanupExpiredVerificationCodes_repeatsUntilPartialBatch() {
        VerificationCodeCleanupService cleanupService = mock(VerificationCodeCleanupService.class);
        VerificationCodeRetentionProperties properties = new VerificationCodeRetentionProperties();
        properties.setCleanupBatchSize(2);
        properties.setPendingRecoveryBatchSize(2);
        properties.setPendingRecoveryMaxBatches(5);
        when(cleanupService.recoverStalePendingDeliveries()).thenReturn(2, 2, 1);
        when(cleanupService.deleteExpiredTerminalBatch()).thenReturn(2, 2, 1);
        VerificationCodeCleanupScheduler scheduler = new VerificationCodeCleanupScheduler(cleanupService, properties);

        scheduler.cleanupExpiredVerificationCodes();

        var ordered = inOrder(cleanupService);
        ordered.verify(cleanupService, times(3)).recoverStalePendingDeliveries();
        ordered.verify(cleanupService, times(3)).deleteExpiredTerminalBatch();
    }

    @Test
    void cleanupExpiredVerificationCodes_capsPendingRecoveryBatchCount() {
        VerificationCodeCleanupService cleanupService = mock(VerificationCodeCleanupService.class);
        VerificationCodeRetentionProperties properties = new VerificationCodeRetentionProperties();
        properties.setPendingRecoveryBatchSize(2);
        properties.setPendingRecoveryMaxBatches(3);
        when(cleanupService.recoverStalePendingDeliveries()).thenReturn(2);
        VerificationCodeCleanupScheduler scheduler = new VerificationCodeCleanupScheduler(cleanupService, properties);

        scheduler.cleanupExpiredVerificationCodes();

        verify(cleanupService, times(3)).recoverStalePendingDeliveries();
    }
}
