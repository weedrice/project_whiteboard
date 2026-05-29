package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;

public interface NotificationStreamPublisher {

    void publish(Long userId, NotificationResponse.NotificationSummary summary);
}
