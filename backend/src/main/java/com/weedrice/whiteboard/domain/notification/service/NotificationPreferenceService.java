package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.user.repository.UserNotificationSettingsRepository;
import org.springframework.stereotype.Service;

@Service
class NotificationPreferenceService {

    private final UserNotificationSettingsRepository userNotificationSettingsRepository;

    NotificationPreferenceService(UserNotificationSettingsRepository userNotificationSettingsRepository) {
        this.userNotificationSettingsRepository = userNotificationSettingsRepository;
    }

    boolean isSelfNotification(NotificationEvent event) {
        return event != null
                && event.getActor() != null
                && event.getUserToNotify() != null
                && event.getUserToNotify().getUserId() != null
                && event.getUserToNotify().getUserId().equals(event.getActor().getUserId());
    }

    boolean isNotificationEnabled(NotificationEvent event) {
        if (event == null
                || event.getUserToNotify() == null
                || event.getUserToNotify().getUserId() == null
                || event.getNotificationType() == null) {
            return false;
        }

        return userNotificationSettingsRepository
                .findByUserIdAndNotificationType(event.getUserToNotify().getUserId(), event.getNotificationType())
                .map(setting -> Boolean.TRUE.equals(setting.getIsEnabled()))
                .orElse(true);
    }
}
