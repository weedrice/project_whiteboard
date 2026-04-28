package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
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
                               NotificationCommandService commandService,
                               NotificationStreamService streamService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.commandService = commandService;
        this.streamService = streamService;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleNotificationEvent(NotificationEvent event) {
        Notification notification = commandService.handleNotificationEvent(event);
        if (notification != null) {
            deliverNotificationBestEffort(event.getUserToNotify().getUserId(), notification);
        }
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

    private void deliverNotificationBestEffort(Long userId, Notification notification) {
        try {
            streamService.deliverNotification(userId, notification);
        } catch (RuntimeException ignored) {
            // SSE delivery is best-effort; notification persistence must not be rolled back by stream failures.
        }
    }
}
