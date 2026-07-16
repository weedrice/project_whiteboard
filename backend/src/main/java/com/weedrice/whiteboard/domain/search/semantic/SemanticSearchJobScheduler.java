package com.weedrice.whiteboard.domain.search.semantic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class SemanticSearchJobScheduler {

    private final SemanticSearchJobService jobService;

    @Scheduled(cron = "0 * * * * ?", scheduler = "semanticSearchTaskScheduler")
    public void processPendingSemanticSearchJobs() {
        int processedCount = jobService.processPendingJobs();
        if (processedCount > 0) {
            log.info("Processed {} semantic search job(s)", processedCount);
        }
    }
}
