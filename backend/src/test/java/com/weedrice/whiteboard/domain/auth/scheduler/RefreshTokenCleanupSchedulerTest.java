package com.weedrice.whiteboard.domain.auth.scheduler;

import com.weedrice.whiteboard.domain.auth.service.RefreshTokenCleanupService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenCleanupSchedulerTest {

    @Test
    void cleanupExpiredRefreshTokens_stopsAfterPartialBatch() {
        RefreshTokenCleanupService cleanupService = mock(RefreshTokenCleanupService.class);
        when(cleanupService.deleteExpiredBatch())
                .thenReturn(RefreshTokenCleanupService.CLEANUP_BATCH_SIZE, 17);
        RefreshTokenCleanupScheduler scheduler = new RefreshTokenCleanupScheduler(cleanupService);

        scheduler.cleanupExpiredRefreshTokens();

        verify(cleanupService, times(2)).deleteExpiredBatch();
    }

    @Test
    void cleanupExpiredRefreshTokens_capsBatchesPerRun() {
        RefreshTokenCleanupService cleanupService = mock(RefreshTokenCleanupService.class);
        when(cleanupService.deleteExpiredBatch()).thenReturn(RefreshTokenCleanupService.CLEANUP_BATCH_SIZE);
        RefreshTokenCleanupScheduler scheduler = new RefreshTokenCleanupScheduler(cleanupService);

        scheduler.cleanupExpiredRefreshTokens();

        verify(cleanupService, times(20)).deleteExpiredBatch();
    }
}
