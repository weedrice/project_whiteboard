package com.weedrice.whiteboard.domain.tag.service;

import com.weedrice.whiteboard.domain.tag.config.TagCleanupProperties;
import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.dao.TransientDataAccessResourceException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagCleanupServiceTest {

    @Mock
    private TagCleanupBatchWorker batchWorker;

    private TagCleanupService tagCleanupService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-11T18:30:00Z"), DateTimeUtils.KST_ZONE_ID);
        TagCleanupProperties properties = new TagCleanupProperties();
        properties.setBatchSize(2);
        properties.setMaxBatches(3);
        properties.setMaxRetryAttempts(2);
        tagCleanupService = new TagCleanupService(batchWorker, properties, clock);
    }

    @Test
    void cleanupOrphanTags_deletesTagsOlderThanTwentyFourHours() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 11, 3, 30);
        when(batchWorker.deleteBatch(cutoff, 2)).thenReturn(2, 1);

        int deletedCount = tagCleanupService.cleanupOrphanTags();

        assertThat(deletedCount).isEqualTo(3);
        verify(batchWorker, times(2)).deleteBatch(cutoff, 2);
    }

    @Test
    void cleanupOrphanTags_stopsAtConfiguredMaximumBatchCount() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 11, 3, 30);
        when(batchWorker.deleteBatch(cutoff, 2)).thenReturn(2);

        assertThat(tagCleanupService.cleanupOrphanTags()).isEqualTo(6);

        verify(batchWorker, times(3)).deleteBatch(cutoff, 2);
    }

    @Test
    void cleanupOrphanTags_retriesTransientBatchFailure() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 11, 3, 30);
        when(batchWorker.deleteBatch(cutoff, 2))
                .thenThrow(new TransientDataAccessResourceException("locked"))
                .thenReturn(1);

        assertThat(tagCleanupService.cleanupOrphanTags()).isEqualTo(1);

        verify(batchWorker, times(2)).deleteBatch(cutoff, 2);
    }
}
