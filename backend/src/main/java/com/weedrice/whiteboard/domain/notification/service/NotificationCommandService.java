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
    private final NotificationStreamService streamService;

    NotificationCommandService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               NotificationPreferenceService preferenceService,
                               NotificationStreamService streamService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.preferenceService = preferenceService;
        this.streamService = streamService;
    }

    void handleNotificationEvent(NotificationEvent event) {
        if (preferenceService.isSelfNotification(event) || !preferenceService.isNotificationEnabled(event)) {
            return;
        }

        Notification notification = notificationRepository.save(Notification.builder()
                .user(event.getUserToNotify())
                .actor(event.getActor())
                .actorAgent(event.getActorAgent())
                .notificationType(event.getNotificationType())
                .sourceType(event.getSourceType())
                .sourceId(event.getSourceId())
                .content(event.getContent())
                .build());

        streamService.deliverNotification(event.getUserToNotify().getUserId(), notification);
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
