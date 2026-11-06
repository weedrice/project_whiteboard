package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Revalidates source visibility for every push attempt, including payloads queued before metadata was added. */
@Service
@RequiredArgsConstructor
class NotificationPushDeliveryPolicy {
    private final PushPayloadFactory payloadFactory;
    private final NotificationRepository notificationRepository;
    private final NotificationSourceDeliveryPolicy sourceDeliveryPolicy;

    @Transactional(readOnly = true)
    public boolean canDeliver(Long receiverUserId, String payload) {
        PushPayloadFactory.SourceSnapshot source = payloadFactory.readSource(payload);
        if (source == null) {
            return false;
        }
        if (source.notificationType() != null) {
            return sourceDeliveryPolicy.canDeliver(source.notificationType(),
                    NotificationSourceType.fromDatabaseValue(source.sourceType()).orElse(null),
                    source.sourceId(), source.actorUserId(), receiverUserId);
        }
        // Older snapshots carry the stable notification tag, never a trustworthy post ID in their URL.
        if (source.notificationId() == null) {
            return false;
        }
        Notification notification = notificationRepository.findById(source.notificationId()).orElse(null);
        if (notification == null || notification.getUser() == null
                || !Objects.equals(receiverUserId, notification.getUser().getUserId())) {
            return false;
        }
        return sourceDeliveryPolicy.canDeliver(
                notification.getNotificationType() == null ? null : notification.getNotificationType().name(),
                NotificationSourceType.fromDatabaseValue(notification.getSourceType()).orElse(null),
                notification.getSourceId(),
                notification.getActor() == null ? null : notification.getActor().getUserId(),
                receiverUserId);
    }
}
