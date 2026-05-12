package com.weedrice.whiteboard.domain.tag.scheduler;

import com.weedrice.whiteboard.domain.tag.service.TagCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TagCleanupScheduler {

    private final TagCleanupService tagCleanupService;

    @Scheduled(cron = "0 20 3 * * ?", zone = "Asia/Seoul")
    public void cleanupOrphanTags() {
        int deletedCount = tagCleanupService.cleanupOrphanTags();
        if (deletedCount > 0) {
            log.info("Deleted {} orphan tag(s)", deletedCount);
        }
    }
}
