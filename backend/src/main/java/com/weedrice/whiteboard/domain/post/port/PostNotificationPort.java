package com.weedrice.whiteboard.domain.post.port;

import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;

/** Consumer-owned boundary for notifications emitted by post workflows. */
public interface PostNotificationPort {

    void publishSystemNotification(
            Long recipientUserId,
            NotificationSourceType sourceType,
            Long sourceId,
            String messageKey,
            String messageArgument);
}
