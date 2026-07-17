package com.weedrice.whiteboard.domain.notification.service;

public interface NotificationStreamControl {

    void disconnectUser(Long userId);

    void invalidateCommentTopic(Long postId);

    void invalidateCommentTopicsForUser(Long userId);
}
