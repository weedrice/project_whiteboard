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

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationPreferenceService preferenceService;

    NotificationCommandService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               NotificationPreferenceService preferenceService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
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
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!notification.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        notification.read();
    }

    void readAllNotifications(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        notificationRepository.readAllByUser(user);
    }
}
