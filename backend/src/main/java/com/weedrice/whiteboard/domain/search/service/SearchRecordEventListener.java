package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.search.event.SearchRecordedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchRecordEventListener {

    private final SearchStatisticCommandService searchStatisticCommandService;

    @Async("observabilityTaskExecutor")
    @EventListener
    public void handle(SearchRecordedEvent event) {
        try {
            searchStatisticCommandService.recordSearchStatistic(event.keyword(), event.searchDate());
        } catch (RuntimeException e) {
            log.warn("Failed to record search asynchronously. failureType={}", e.getClass().getSimpleName());
        }
    }
}
