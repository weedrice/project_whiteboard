package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final NotificationEventHandler eventHandler;
    private final NotificationQueryService queryService;
    private final NotificationReadCommandService readCommandService;
    private final UserRepository userRepository;

    public NotificationService(NotificationEventHandler eventHandler,
                               NotificationQueryService queryService,
                               NotificationReadCommandService readCommandService,
                               UserRepository userRepository) {
        this.eventHandler = eventHandler;
        this.queryService = queryService;
        this.readCommandService = readCommandService;
        this.userRepository = userRepository;
    }

    public void handleNotificationEvent(NotificationEvent event) {
        eventHandler.handleNotificationEvent(event);
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
        validateUserExists(userId);
        return queryService.getUnreadNotificationCount(userId);
    }

    private void validateUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }
}
