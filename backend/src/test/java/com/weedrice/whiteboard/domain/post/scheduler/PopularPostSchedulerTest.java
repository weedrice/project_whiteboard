package com.weedrice.whiteboard.domain.post.scheduler;

import com.weedrice.whiteboard.domain.post.service.PopularPostAggregationService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PopularPostSchedulerTest {

    private final PopularPostAggregationService aggregationService = mock(PopularPostAggregationService.class);
    private final PopularPostScheduler scheduler = new PopularPostScheduler(aggregationService);

    @Test
    void aggregatePopularPosts_delegatesToTransactionalService() {
        when(aggregationService.aggregate()).thenReturn(true);

        scheduler.aggregatePopularPosts();

        verify(aggregationService).aggregate();
    }

    @Test
    void aggregatePopularPosts_skipsWhenLockIsUnavailable() {
        when(aggregationService.aggregate()).thenReturn(false);

        scheduler.aggregatePopularPosts();

        verify(aggregationService).aggregate();
    }
}
