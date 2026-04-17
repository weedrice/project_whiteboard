package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import com.weedrice.whiteboard.domain.feed.repository.UserFeedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserFeedCommandService {

    private final UserFeedRepository userFeedRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFeedIfAbsent(UserFeed feed) {
        userFeedRepository.insertIgnore(
                feed.getTargetUser().getUserId(),
                feed.getFeedType(),
                feed.getContentType(),
                feed.getContentId(),
                feed.getSourceCriteria(),
                feed.getCriteriaId());
    }
}
