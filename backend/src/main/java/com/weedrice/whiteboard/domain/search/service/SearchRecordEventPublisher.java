package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.search.event.SearchRecordedEvent;
import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchRecordEventPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final RecentSearchCommandService recentSearchCommandService;

    public void publish(Long userId, String keyword) {
        String canonicalKeyword = SearchRequestNormalizer.canonicalizeOptionalKeyword(keyword);
        if (canonicalKeyword == null) {
            return;
        }

        var searchDate = DateTimeUtils.nowKST().toLocalDate();
        if (userId != null) {
            try {
                recentSearchCommandService.recordRecentSearch(userId, canonicalKeyword);
            } catch (RuntimeException e) {
                log.warn("Failed to record recent search. failureType={}", e.getClass().getSimpleName());
            }
        }
        try {
            eventPublisher.publishEvent(new SearchRecordedEvent(canonicalKeyword, searchDate));
        } catch (RuntimeException e) {
            log.warn("Failed to publish search record event. failureType={}", e.getClass().getSimpleName());
        }
    }
}
