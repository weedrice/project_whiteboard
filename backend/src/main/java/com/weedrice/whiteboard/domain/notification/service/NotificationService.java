package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserNotificationSettingsRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationCommandService commandService;
    private final NotificationStreamService streamService;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               UserNotificationSettingsRepository userNotificationSettingsRepository) {
        NotificationPreferenceService preferenceService =
                new NotificationPreferenceService(userNotificationSettingsRepository);
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.streamService = new NotificationStreamService();
        this.commandService = new NotificationCommandService(
                notificationRepository,
                userRepository,
                preferenceService,
                streamService);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleNotificationEvent(NotificationEvent event) {
        commandService.handleNotificationEvent(event);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SseEmitter subscribe(Long userId) {
        return streamService.subscribe(userId);
    }

    @Scheduled(fixedRate = 25_000)
    public void sendHeartbeat() {
        streamService.sendHeartbeat();
    }

    public NotificationResponse getNotifications(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Page<Notification> notificationPage = notificationRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        return NotificationResponse.from(notificationPage);
    }

    @Transactional
    public void readNotification(Long userId, Long notificationId) {
        commandService.readNotification(userId, notificationId);
    }

    @Transactional
    public void readAllNotifications(Long userId) {
        commandService.readAllNotifications(userId);
    }

    public long getUnreadNotificationCount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return notificationRepository.countByUserAndIsRead(user, false);
    }
}
