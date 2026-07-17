package com.weedrice.whiteboard.domain.notification.service;

record NotificationStreamInvalidationEvent(Type type, Long targetId) {

    enum Type {
        DISCONNECT_USER,
        INVALIDATE_POST,
        INVALIDATE_USER_TOPICS
    }

    static NotificationStreamInvalidationEvent disconnectUser(Long userId) {
        return new NotificationStreamInvalidationEvent(Type.DISCONNECT_USER, userId);
    }

    static NotificationStreamInvalidationEvent invalidatePost(Long postId) {
        return new NotificationStreamInvalidationEvent(Type.INVALIDATE_POST, postId);
    }

    static NotificationStreamInvalidationEvent invalidateUserTopics(Long userId) {
        return new NotificationStreamInvalidationEvent(Type.INVALIDATE_USER_TOPICS, userId);
    }
}
