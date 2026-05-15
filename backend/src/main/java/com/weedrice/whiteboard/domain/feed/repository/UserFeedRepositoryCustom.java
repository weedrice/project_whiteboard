package com.weedrice.whiteboard.domain.feed.repository;

import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserFeedRepositoryCustom {

    Page<UserFeed> findVisibleByTargetUserOrderByCreatedAtDesc(
            UserFeedVisibilityCondition visibilityCondition,
            Pageable pageable);
}
