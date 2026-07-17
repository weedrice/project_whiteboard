package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final NotificationQueryService queryService;
    private final NotificationReadCommandService readCommandService;
    private final UserRepository userRepository;
    private final NotificationCommandService commandService;
    private final NotificationDeliveryPublisher deliveryPublisher;

    public NotificationService(NotificationQueryService queryService,
                               NotificationReadCommandService readCommandService,
                               UserRepository userRepository,
                               NotificationCommandService commandService,
                               NotificationDeliveryPublisher deliveryPublisher) {
        this.queryService = queryService;
        this.readCommandService = readCommandService;
        this.userRepository = userRepository;
        this.commandService = commandService;
        this.deliveryPublisher = deliveryPublisher;
    }

    @org.springframework.transaction.annotation.Transactional
    public void handleNotificationEvent(NotificationEvent event) {
        com.weedrice.whiteboard.domain.notification.entity.Notification notification =
                commandService.handleNotificationEvent(event);
        if (notification != null) {
            deliveryPublisher.publishAfterCommit(event.getUserToNotify().getUserId(), notification);
        }
    }

    public void validateStreamSubscription(Long userId) {
        validateUserExists(userId);
    }

    public NotificationResponse getNotifications(Long userId, Pageable pageable) {
        validateUserExists(userId);
        return queryService.getNotifications(userId, pageable);
    }

    public void readNotification(Long userId, Long notificationId) {
        validateUserExists(userId);
        readCommandService.readNotification(userId, notificationId);
    }

    public void readAllNotifications(Long userId) {
        validateUserExists(userId);
        readCommandService.readAllNotifications(userId);
    }

    public long getUnreadNotificationCount(Long userId) {
        validateActiveUser(userId);
        return queryService.getUnreadNotificationCount(userId);
    }

    private void validateUserExists(Long userId) {
        validateActiveUser(userId);
    }

    private void validateActiveUser(Long userId) {
        userRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, User.STATUS_ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
