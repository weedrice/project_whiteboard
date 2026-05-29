package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
class NotificationEventHandler {

    private final NotificationCommandService commandService;
    private final NotificationStreamPublisher streamPublisher;
    private final NotificationTargetUrlResolver targetUrlResolver;

    NotificationEventHandler(NotificationCommandService commandService,
                             NotificationStreamPublisher streamPublisher) {
        this(commandService, streamPublisher, NotificationTargetUrlResolver.noop());
    }

    @Autowired
    NotificationEventHandler(NotificationCommandService commandService,
                             NotificationStreamPublisher streamPublisher,
                             NotificationTargetUrlResolver targetUrlResolver) {
        this.commandService = commandService;
        this.streamPublisher = streamPublisher;
        this.targetUrlResolver = targetUrlResolver;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleNotificationEvent(NotificationEvent event) {
        Notification notification = commandService.handleNotificationEvent(event);
        if (notification != null) {
            deliverNotificationAfterCommit(event.getUserToNotify().getUserId(), notification);
        }
    }

    private void deliverNotificationBestEffort(Long userId, Notification notification) {
        try {
            streamPublisher.publish(
                    userId,
                    NotificationResponse.NotificationSummary.from(
                            notification,
                            targetUrlResolver.resolve(notification)));
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
