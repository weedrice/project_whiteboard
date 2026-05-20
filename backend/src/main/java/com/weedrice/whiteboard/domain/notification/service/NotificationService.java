package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class NotificationService {

    private final NotificationEventHandler eventHandler;
    private final NotificationQueryService queryService;
    private final NotificationReadCommandService readCommandService;
    private final NotificationSseFacade sseFacade;
    private final UserRepository userRepository;

    public NotificationService(NotificationEventHandler eventHandler,
                               NotificationQueryService queryService,
                               NotificationReadCommandService readCommandService,
                               NotificationSseFacade sseFacade,
                               UserRepository userRepository) {
        this.eventHandler = eventHandler;
        this.queryService = queryService;
        this.readCommandService = readCommandService;
        this.sseFacade = sseFacade;
        this.userRepository = userRepository;
    }

    public void handleNotificationEvent(NotificationEvent event) {
        eventHandler.handleNotificationEvent(event);
    }

    public SseEmitter subscribe(Long userId) {
        validateUserExists(userId);
        return sseFacade.subscribe(userId);
    }

    public void sendHeartbeat() {
        sseFacade.sendHeartbeat();
    }

    public NotificationResponse getNotifications(Long userId, Pageable pageable) {
        validateUserExists(userId);
        return queryService.getNotifications(userId, pageable);
    }

    public void readNotification(Long userId, Long notificationId) {
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
