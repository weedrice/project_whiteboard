package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Service
class NotificationCommandService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceService preferenceService;

    NotificationCommandService(NotificationRepository notificationRepository,
                               NotificationPreferenceService preferenceService) {
        this.notificationRepository = notificationRepository;
        this.preferenceService = preferenceService;
    }

    Notification handleNotificationEvent(NotificationEvent event) {
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
                .content(event.getContent())
                .build());
    }

    void readNotification(Long userId, Long notificationId) {
        int updatedRows = notificationRepository.markReadByNotificationIdAndUserId(notificationId, userId);
        if (updatedRows > 0) {
            return;
        }
        if (notificationRepository.existsByNotificationIdAndUser_UserId(notificationId, userId)) {
            return;
        }
        if (notificationRepository.existsById(notificationId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    void readAllNotifications(Long userId) {
        notificationRepository.readAllByUserId(userId);
    }
}
