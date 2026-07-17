package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.entity.Notification;

record PushDispatchCommand(
        Long userId,
        Long notificationId,
        String content,
        String notificationType) {

    static PushDispatchCommand from(Notification notification) {
        if (notification == null || notification.getUser() == null) {
            return null;
        }
        String type = notification.getNotificationType() == null
                ? "notification"
                : notification.getNotificationType().name().toLowerCase();
        return new PushDispatchCommand(
                notification.getUser().getUserId(),
                notification.getNotificationId(),
                notification.getContent(),
                type);
    }
}
