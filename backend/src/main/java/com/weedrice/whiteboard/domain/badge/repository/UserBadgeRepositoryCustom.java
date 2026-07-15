package com.weedrice.whiteboard.domain.badge.repository;

import java.time.LocalDateTime;

public interface UserBadgeRepositoryCustom {

    int insertIgnore(Long userId, String badgeCode, LocalDateTime acquiredAt);
}
