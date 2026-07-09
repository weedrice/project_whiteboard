package com.weedrice.whiteboard.domain.post.scheduled.scheduler;

import com.weedrice.whiteboard.domain.post.scheduled.service.ScheduledPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledPostPublisherScheduler {

    private final ScheduledPostService scheduledPostService;

    @Scheduled(cron = "0 * * * * ?", zone = "Asia/Seoul")
    public void publishDuePosts() {
        int publishedCount = scheduledPostService.publishDuePosts();
        if (publishedCount > 0) {
            log.info("Published {} scheduled post(s)", publishedCount);
        }
    }
}
