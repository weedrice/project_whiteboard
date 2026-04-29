package com.weedrice.whiteboard.domain.feed.repository;

import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;

public interface UserFeedRepositoryCustom {

    Page<UserFeed> findVisibleByTargetUserOrderByCreatedAtDesc(
            User targetUser,
            Collection<Long> blockedUserIds,
            Pageable pageable);
}
