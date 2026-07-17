package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class NotificationEventHandler {

    private final NotificationDeliveryJobService deliveryJobService;

    @EventListener
    @Transactional
    public void handleNotificationEvent(NotificationEvent event) {
        deliveryJobService.enqueue(event);
    }
}
