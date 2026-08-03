package com.weedrice.whiteboard.domain.post.scheduler;

import com.weedrice.whiteboard.domain.post.service.PostDraftCleanupCoordinator;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostDraftCleanupScheduler {

    private final PostDraftCleanupCoordinator postDraftCleanupCoordinator;

    @Scheduled(cron = "0 15 3 * * ?", zone = "Asia/Seoul")
    public void cleanupExpiredDrafts() {
        postDraftCleanupCoordinator.cleanupExpiredDrafts();
    }
}
