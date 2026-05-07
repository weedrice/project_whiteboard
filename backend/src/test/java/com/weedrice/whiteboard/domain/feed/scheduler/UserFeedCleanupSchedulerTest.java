package com.weedrice.whiteboard.domain.feed.scheduler;

import com.weedrice.whiteboard.domain.feed.service.UserFeedCleanupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserFeedCleanupSchedulerTest {

    @Mock
    private UserFeedCleanupService userFeedCleanupService;

    @InjectMocks
    private UserFeedCleanupScheduler scheduler;

    @Test
    void cleanupHardStalePostFeeds_delegatesToCleanupService() {
        scheduler.cleanupHardStalePostFeeds();

        verify(userFeedCleanupService).cleanupHardStalePostFeeds();
    }
}
