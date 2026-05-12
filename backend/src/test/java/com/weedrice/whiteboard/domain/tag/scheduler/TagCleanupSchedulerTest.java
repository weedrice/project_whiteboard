package com.weedrice.whiteboard.domain.tag.scheduler;

import com.weedrice.whiteboard.domain.tag.service.TagCleanupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagCleanupSchedulerTest {

    @Mock
    private TagCleanupService tagCleanupService;

    @InjectMocks
    private TagCleanupScheduler scheduler;

    @Test
    void cleanupOrphanTags_delegatesToCleanupService() {
        when(tagCleanupService.cleanupOrphanTags()).thenReturn(2);

        scheduler.cleanupOrphanTags();

        verify(tagCleanupService).cleanupOrphanTags();
    }
}
