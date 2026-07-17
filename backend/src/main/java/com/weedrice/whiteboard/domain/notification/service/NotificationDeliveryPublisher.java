package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
class NotificationDeliveryPublisher {

    private final NotificationStreamPublisher streamPublisher;
    private final NotificationTargetUrlResolver targetUrlResolver;

    void publishAfterCommit(Long userId, Notification notification) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishBestEffort(userId, notification);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishBestEffort(userId, notification);
            }
        });
    }

    private void publishBestEffort(Long userId, Notification notification) {
        try {
            streamPublisher.publish(
                    userId,
                    NotificationResponse.NotificationSummary.from(
                            notification,
                            targetUrlResolver.resolve(notification)));
        } catch (RuntimeException exception) {
            log.warn("Failed to deliver notification SSE. userId={}, notificationId={}, exceptionType={}",
                    userId,
                    notification.getNotificationId(),
                    exception.getClass().getSimpleName());
        }
    }
}
