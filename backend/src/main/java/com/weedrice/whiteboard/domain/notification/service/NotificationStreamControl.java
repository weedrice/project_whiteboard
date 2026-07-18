package com.weedrice.whiteboard.domain.notification.service;

import java.util.UUID;

public interface NotificationStreamControl {

    void disconnectUser(Long userId);

    void disconnectSessionFamily(Long userId, UUID sessionFamilyId);

    void disconnectOtherSessionFamilies(Long userId, UUID retainedSessionFamilyId);

    void invalidateCommentTopic(Long postId);

    void invalidateCommentTopicsForBoard(Long boardId);

    void invalidateCommentTopicsForUser(Long userId);
}
