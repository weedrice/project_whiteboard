package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.search.event.SearchRecordedEvent;
import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchRecordEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publish(Long userId, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }

        try {
            eventPublisher.publishEvent(new SearchRecordedEvent(
                    userId,
                    keyword.trim(),
                    DateTimeUtils.nowKST().toLocalDate()));
        } catch (RuntimeException e) {
            log.warn("Failed to publish search record event. userId={}, keyword={}", userId, keyword, e);
        }
    }
}
