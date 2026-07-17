package com.weedrice.whiteboard.domain.notification.repository;

public interface UserKeywordSubscriptionRepositoryCustom {
    int insertIgnore(Long userId, String keyword);
}
