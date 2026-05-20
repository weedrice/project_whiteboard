package com.weedrice.whiteboard.domain.notification.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class NotificationReadCommandService {

    private final NotificationCommandService commandService;

    NotificationReadCommandService(NotificationCommandService commandService) {
        this.commandService = commandService;
    }

    @Transactional
    public void readNotification(Long userId, Long notificationId) {
        commandService.readNotification(userId, notificationId);
    }

    @Transactional
    public void readAllNotifications(Long userId) {
        commandService.readAllNotifications(userId);
    }
}
