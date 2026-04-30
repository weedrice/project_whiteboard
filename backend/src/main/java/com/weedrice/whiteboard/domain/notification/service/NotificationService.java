package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;

@Slf4j
@Service
@Transactional(readOnly = true)
public class NotificationService {

    private static final int DEFAULT_NOTIFICATION_PAGE_SIZE = 20;
    private static final Sort NOTIFICATION_LIST_SORT = Sort.by(Sort.Order.desc("createdAt"));
    private static final Set<String> ALLOWED_NOTIFICATION_SORTS = Set.of("createdAt");

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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleNotificationEvent(NotificationEvent event) {
        Notification notification = commandService.handleNotificationEvent(event);
        if (notification != null) {
            deliverNotificationAfterCommit(event.getUserToNotify().getUserId(), notification);
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
        validateUserExists(userId);
        Pageable safePageable = PageRequestUtils.of(
                pageable,
                DEFAULT_NOTIFICATION_PAGE_SIZE,
                NOTIFICATION_LIST_SORT,
                ALLOWED_NOTIFICATION_SORTS);
        Page<Notification> notificationPage =
                notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(userId, safePageable);
        return NotificationResponse.from(notificationPage);
    }

    @Transactional
    public void readNotification(Long userId, Long notificationId) {
        commandService.readNotification(userId, notificationId);
    }

    @Transactional
    public void readAllNotifications(Long userId) {
        validateUserExists(userId);
        commandService.readAllNotifications(userId);
    }

    public long getUnreadNotificationCount(Long userId) {
        validateUserExists(userId);
        return notificationRepository.countByUser_UserIdAndIsRead(userId, false);
    }

    private void validateUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private void deliverNotificationBestEffort(Long userId, Notification notification) {
        try {
            streamService.deliverNotification(userId, notification);
        } catch (RuntimeException e) {
            log.warn(
                    "Failed to deliver notification SSE. userId={}, notificationId={}, exceptionType={}",
                    userId,
                    notification.getNotificationId(),
                    e.getClass().getSimpleName());
            // SSE delivery is best-effort; notification persistence must not be rolled back by stream failures.
        }
    }

    private void deliverNotificationAfterCommit(Long userId, Notification notification) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deliverNotificationBestEffort(userId, notification);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deliverNotificationBestEffort(userId, notification);
            }
        });
    }
}
