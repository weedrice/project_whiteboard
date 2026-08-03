package com.weedrice.whiteboard.domain.post.scheduled.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledPostCleanupServiceTest {

    @Test
    void repeatsFailedCleanupInBoundedBatches() {
        ScheduledPostCleanupBatchService batchService = mock(ScheduledPostCleanupBatchService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), ZoneOffset.UTC);
        LocalDateTime now = LocalDateTime.of(2026, 8, 3, 0, 0);
        LocalDateTime cutoff = now.minusDays(30);
        when(batchService.cleanupExpiredFailedBatch(cutoff, now, 100)).thenReturn(100, 2);
        ScheduledPostCleanupService service = new ScheduledPostCleanupService(batchService, clock);

        assertThat(service.cleanupExpiredFailedPosts()).isEqualTo(102);

        verify(batchService, org.mockito.Mockito.times(2))
                .cleanupExpiredFailedBatch(cutoff, now, 100);
    }
}
