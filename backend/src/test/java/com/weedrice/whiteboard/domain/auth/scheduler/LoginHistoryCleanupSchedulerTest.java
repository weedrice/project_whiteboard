package com.weedrice.whiteboard.domain.auth.scheduler;

import com.weedrice.whiteboard.domain.auth.LoginHistoryRetentionProperties;
import com.weedrice.whiteboard.domain.auth.service.LoginHistoryCleanupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginHistoryCleanupSchedulerTest {

    @Mock private LoginHistoryCleanupService cleanupService;

    @Test
    void cleanupExpiredLoginHistories_stopsAfterPartialBatch() {
        LoginHistoryRetentionProperties properties = new LoginHistoryRetentionProperties();
        properties.setCleanupBatchSize(500);
        properties.setCleanupMaxBatches(5);
        when(cleanupService.deleteExpiredBatch()).thenReturn(500, 500, 12);
        LoginHistoryCleanupScheduler scheduler = new LoginHistoryCleanupScheduler(cleanupService, properties);

        scheduler.cleanupExpiredLoginHistories();

        verify(cleanupService, times(3)).deleteExpiredBatch();
    }

    @Test
    void cleanupExpiredLoginHistories_honorsMaximumBatchCount() {
        LoginHistoryRetentionProperties properties = new LoginHistoryRetentionProperties();
        properties.setCleanupBatchSize(500);
        properties.setCleanupMaxBatches(2);
        when(cleanupService.deleteExpiredBatch()).thenReturn(500);
        LoginHistoryCleanupScheduler scheduler = new LoginHistoryCleanupScheduler(cleanupService, properties);

        scheduler.cleanupExpiredLoginHistories();

        verify(cleanupService, times(2)).deleteExpiredBatch();
    }
}
