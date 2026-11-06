package com.weedrice.whiteboard.domain.notification.service;

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
    private final NotificationQueryService queryService;

    void publishAfterCommit(Long userId, Notification notification) {
        Long notificationId = notification.getNotificationId();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishBestEffort(userId, notificationId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishBestEffort(userId, notificationId);
            }
        });
    }

    private void publishBestEffort(Long userId, Long notificationId) {
        try {
            queryService.getNotificationForDelivery(userId, notificationId)
                    .ifPresent(summary -> streamPublisher.publish(userId, summary));
        } catch (RuntimeException exception) {
            log.warn("Failed to deliver notification SSE. userId={}, notificationId={}, exceptionType={}",
                    userId,
                    notificationId,
                    exception.getClass().getSimpleName());
        }
    }
}
