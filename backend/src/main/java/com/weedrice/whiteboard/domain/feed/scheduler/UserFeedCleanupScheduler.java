package com.weedrice.whiteboard.domain.feed.scheduler;

import com.weedrice.whiteboard.domain.feed.service.UserFeedCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserFeedCleanupScheduler {

    private final UserFeedCleanupService userFeedCleanupService;

    @Scheduled(cron = "0 30 3 * * ?", zone = "Asia/Seoul")
    public void cleanupHardStalePostFeeds() {
        userFeedCleanupService.cleanupHardStalePostFeeds();
    }
}
