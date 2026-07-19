package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.entity.Notification;

record PushDispatchCommand(
        Long userId,
        Long actorUserId,
        Long notificationId,
        String content,
        String notificationType,
        String targetUrl) {

    static PushDispatchCommand from(Notification notification, String targetUrl) {
        if (notification == null || notification.getUser() == null) {
            return null;
        }
        String type = notification.getNotificationType() == null
                ? "notification"
                : notification.getNotificationType().name().toLowerCase();
        return new PushDispatchCommand(
                notification.getUser().getUserId(),
                notification.getActor() == null ? null : notification.getActor().getUserId(),
                notification.getNotificationId(),
                notification.getContent(),
                type,
                targetUrl);
    }
}
