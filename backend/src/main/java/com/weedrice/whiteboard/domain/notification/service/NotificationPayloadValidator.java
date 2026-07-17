package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob;
import org.springframework.stereotype.Component;

@Component
class NotificationPayloadValidator {

    String validate(NotificationEvent event) {
        if (event == null || event.getUserToNotify() == null || event.getUserToNotify().getUserId() == null) {
            return "MISSING_RECEIVER";
        }
        if (event.getNotificationType() == null || event.getSourceType() == null) {
            return "MISSING_TYPE";
        }
        if (event.getSourceId() == null) {
            return "MISSING_SOURCE";
        }
        if (!hasText(event.getContent()) && !hasText(event.getMessageKey())) {
            return "MISSING_CONTENT";
        }
        return null;
    }

    String validate(NotificationDeliveryJob job) {
        if (job.getReceiverUserId() == null) return "MISSING_RECEIVER";
        if (job.getNotificationType() == null || job.getSourceType() == null) return "MISSING_TYPE";
        if (job.getSourceId() == null) return "MISSING_SOURCE";
        if (!hasText(job.getContent()) && !hasText(job.getMessageKey())) return "MISSING_CONTENT";
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
