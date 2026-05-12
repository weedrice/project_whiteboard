package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Service
class NotificationCommandService {
    private static final int MAX_CONTENT_LENGTH = 255;

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceService preferenceService;

    NotificationCommandService(NotificationRepository notificationRepository,
                               NotificationPreferenceService preferenceService) {
        this.notificationRepository = notificationRepository;
        this.preferenceService = preferenceService;
    }

    Notification handleNotificationEvent(NotificationEvent event) {
        if (!hasRequiredPayload(event)) {
            return null;
        }
        String content = normalizeContent(event.getContent());
        if (content == null) {
            return null;
        }
        if (preferenceService.isSelfNotification(event) || !preferenceService.isNotificationEnabled(event)) {
            return null;
        }

        return notificationRepository.save(Notification.builder()
                .user(event.getUserToNotify())
                .actor(event.getActor())
                .actorAgent(event.getActorAgent())
                .notificationType(event.getNotificationType())
                .sourceType(event.getSourceType())
                .sourceId(event.getSourceId())
                .content(content)
                .build());
    }

    private boolean hasRequiredPayload(NotificationEvent event) {
        return event != null
                && event.getUserToNotify() != null
                && event.getUserToNotify().getUserId() != null
                && event.getNotificationType() != null
                && hasText(event.getSourceType());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeContent(String content) {
        if (content == null) {
            return null;
        }
        String normalizedContent = content.strip();
        if (normalizedContent.isBlank()) {
            return null;
        }
        if (normalizedContent.codePointCount(0, normalizedContent.length()) > MAX_CONTENT_LENGTH) {
            int endIndex = normalizedContent.offsetByCodePoints(0, MAX_CONTENT_LENGTH);
            return normalizedContent.substring(0, endIndex);
        }
        return normalizedContent;
    }

    void readNotification(Long userId, Long notificationId) {
        int updatedRows = notificationRepository.markReadByNotificationIdAndUserId(notificationId, userId);
        if (updatedRows > 0) {
            return;
        }
        if (notificationRepository.existsByNotificationIdAndUser_UserId(notificationId, userId)) {
            return;
        }
        throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    void readAllNotifications(Long userId) {
        notificationRepository.readAllByUserId(userId);
    }
}
