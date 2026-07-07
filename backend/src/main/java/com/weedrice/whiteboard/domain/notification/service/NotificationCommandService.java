package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Service
class NotificationCommandService {
    private static final int MAX_CONTENT_LENGTH = 255;

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceService preferenceService;
    private final UserRepository userRepository;

    NotificationCommandService(NotificationRepository notificationRepository,
                               NotificationPreferenceService preferenceService,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.preferenceService = preferenceService;
        this.userRepository = userRepository;
    }

    Notification handleNotificationEvent(NotificationEvent event) {
        if (!hasRequiredPayload(event)) {
            return null;
        }
        String content = normalizeContent(event.getContent());
        if (content == null) {
            return null;
        }
        User receiver = resolveActiveReceiver(event);
        if (receiver == null) {
            return null;
        }
        if (preferenceService.isSelfNotification(event) || !preferenceService.isNotificationEnabled(event)) {
            return null;
        }

        return notificationRepository.save(Notification.builder()
                .user(receiver)
                .actor(event.getActor())
                .actorAgent(event.getActorAgent())
                .notificationType(event.getNotificationType())
                .sourceType(event.getSourceType().getValue())
                .sourceId(event.getSourceId())
                .content(content)
                .build());
    }

    private boolean hasRequiredPayload(NotificationEvent event) {
        return event != null
                && event.getUserToNotify() != null
                && event.getUserToNotify().getUserId() != null
                && event.getNotificationType() != null
                && event.getSourceType() != null
                && event.getSourceId() != null;
    }

    private User resolveActiveReceiver(NotificationEvent event) {
        return userRepository.findByUserIdAndStatusAndDeletedAtIsNull(
                        event.getUserToNotify().getUserId(),
                        User.STATUS_ACTIVE)
                .orElse(null);
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
        throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    void readAllNotifications(Long userId) {
        notificationRepository.readAllByUserId(userId);
    }
}
