package com.weedrice.whiteboard.domain.search.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchRecordFacade {

    private final SearchRecordEventPublisher searchRecordEventPublisher;

    public void record(Long userId, String keyword) {
        searchRecordEventPublisher.publish(userId, keyword);
    }
}
