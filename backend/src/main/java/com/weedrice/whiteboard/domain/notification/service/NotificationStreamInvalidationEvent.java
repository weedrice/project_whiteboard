package com.weedrice.whiteboard.domain.notification.service;

import java.util.UUID;

record NotificationStreamInvalidationEvent(Type type, Long targetId, UUID sessionFamilyId) {

    enum Type {
        DISCONNECT_USER,
        DISCONNECT_SESSION_FAMILY,
        DISCONNECT_OTHER_SESSION_FAMILIES,
        INVALIDATE_POST,
        INVALIDATE_BOARD,
        INVALIDATE_USER_TOPICS
    }

    static NotificationStreamInvalidationEvent disconnectUser(Long userId) {
        return new NotificationStreamInvalidationEvent(Type.DISCONNECT_USER, userId, null);
    }

    static NotificationStreamInvalidationEvent disconnectSessionFamily(Long userId, UUID sessionFamilyId) {
        return new NotificationStreamInvalidationEvent(Type.DISCONNECT_SESSION_FAMILY, userId, sessionFamilyId);
    }

    static NotificationStreamInvalidationEvent disconnectOtherSessionFamilies(
            Long userId,
            UUID retainedSessionFamilyId) {
        return new NotificationStreamInvalidationEvent(
                Type.DISCONNECT_OTHER_SESSION_FAMILIES,
                userId,
                retainedSessionFamilyId);
    }

    static NotificationStreamInvalidationEvent invalidatePost(Long postId) {
        return new NotificationStreamInvalidationEvent(Type.INVALIDATE_POST, postId, null);
    }

    static NotificationStreamInvalidationEvent invalidateBoard(Long boardId) {
        return new NotificationStreamInvalidationEvent(Type.INVALIDATE_BOARD, boardId, null);
    }

    static NotificationStreamInvalidationEvent invalidateUserTopics(Long userId) {
        return new NotificationStreamInvalidationEvent(Type.INVALIDATE_USER_TOPICS, userId, null);
    }
}
