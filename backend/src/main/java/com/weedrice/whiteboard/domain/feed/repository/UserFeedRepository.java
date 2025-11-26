package com.weedrice.whiteboard.domain.feed.repository;

import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFeedRepository extends JpaRepository<UserFeed, Long> {
    Page<UserFeed> findByTargetUserOrderByCreatedAtDesc(User targetUser, Pageable pageable);
}
