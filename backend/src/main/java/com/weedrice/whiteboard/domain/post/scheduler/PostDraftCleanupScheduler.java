package com.weedrice.whiteboard.domain.post.scheduler;

import com.weedrice.whiteboard.domain.post.service.PostDraftCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostDraftCleanupScheduler {

    private final PostDraftCleanupService postDraftCleanupService;

    @Scheduled(cron = "0 15 3 * * ?", zone = "Asia/Seoul")
    public void cleanupExpiredDrafts() {
        postDraftCleanupService.cleanupExpiredDrafts();
    }
}
