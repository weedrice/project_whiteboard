package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.entity.Notification;

import java.util.Collection;
import java.util.Map;

interface NotificationTargetUrlResolver {

    Map<Long, String> resolveAll(Collection<Notification> notifications);

    default String resolve(Notification notification) {
        if (notification == null || notification.getNotificationId() == null) {
            return null;
        }

        return resolveAll(java.util.List.of(notification)).get(notification.getNotificationId());
    }

}
