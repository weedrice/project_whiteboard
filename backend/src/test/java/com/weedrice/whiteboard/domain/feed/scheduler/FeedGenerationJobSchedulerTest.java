package com.weedrice.whiteboard.domain.feed.scheduler;

import com.weedrice.whiteboard.domain.feed.service.FeedGenerationJobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedGenerationJobSchedulerTest {

    @Mock
    private FeedGenerationJobService feedGenerationJobService;

    @InjectMocks
    private FeedGenerationJobScheduler scheduler;

    @Test
    void processPendingFeedGenerationJobs_delegatesToJobService() {
        when(feedGenerationJobService.processPendingJobs()).thenReturn(2);

        scheduler.processPendingFeedGenerationJobs();

        verify(feedGenerationJobService).processPendingJobs();
    }
}
