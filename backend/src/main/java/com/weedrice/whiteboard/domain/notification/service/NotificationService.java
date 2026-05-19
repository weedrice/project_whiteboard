package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class NotificationService {

    private final NotificationEventHandler eventHandler;
    private final NotificationQueryService queryService;
    private final NotificationReadCommandService readCommandService;
    private final NotificationSseFacade sseFacade;

    public NotificationService(NotificationEventHandler eventHandler,
                               NotificationQueryService queryService,
                               NotificationReadCommandService readCommandService,
                               NotificationSseFacade sseFacade) {
        this.eventHandler = eventHandler;
        this.queryService = queryService;
        this.readCommandService = readCommandService;
        this.sseFacade = sseFacade;
    }

    public void handleNotificationEvent(NotificationEvent event) {
        eventHandler.handleNotificationEvent(event);
    }

    public SseEmitter subscribe(Long userId) {
        return sseFacade.subscribe(userId);
    }

    public void sendHeartbeat() {
        sseFacade.sendHeartbeat();
    }

    public NotificationResponse getNotifications(Long userId, Pageable pageable) {
        return queryService.getNotifications(userId, pageable);
    }

    public void readNotification(Long userId, Long notificationId) {
        readCommandService.readNotification(userId, notificationId);
    }

    public void readAllNotifications(Long userId) {
        readCommandService.readAllNotifications(userId);
    }

    public long getUnreadNotificationCount(Long userId) {
        return queryService.getUnreadNotificationCount(userId);
    }
}
