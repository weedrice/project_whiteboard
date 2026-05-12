package com.weedrice.whiteboard.domain.feed.scheduler;

import com.weedrice.whiteboard.domain.feed.service.FeedGenerationJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedGenerationJobScheduler {

    private final FeedGenerationJobService feedGenerationJobService;

    @Scheduled(cron = "0 * * * * ?")
    public void processPendingFeedGenerationJobs() {
        int processedCount = feedGenerationJobService.processPendingJobs();
        if (processedCount > 0) {
            log.info("Processed {} feed generation job(s)", processedCount);
        }
    }
}
