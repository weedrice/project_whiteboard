package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.actor.AuthorPresentation;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
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
    private final UserRepository userRepository;

    /** Rebuild only public presentation; queued payloads and persisted notification history stay unchanged. */
    @Transactional(readOnly = true)
    public String payloadForDelivery(Long receiverUserId, String payload) {
        PushPayloadFactory.SourceSnapshot source = payloadFactory.readSource(payload);
        if (source == null) {
            return null;
        }
        Notification notification = null;
        Long actorId = source.actorUserId();
        if (actorId == null && source.notificationId() != null) {
            notification = notificationRepository.findById(source.notificationId()).orElse(null);
            if (notification != null && notification.getUser() != null
                    && Objects.equals(receiverUserId, notification.getUser().getUserId())) {
                actorId = notification.getActor() == null ? null : notification.getActor().getUserId();
            }
        }
        if (actorId == null) {
            return payload;
        }
        User actor = userRepository.findById(actorId).orElse(null);
        if (actor == null) {
            return null;
        }
        if (!AuthorPresentation.isWithdrawn(actor.getStatus(), actor.getDeletedAt())) {
            return payload;
        }
        if (notification == null && source.notificationId() != null) {
            notification = notificationRepository.findById(source.notificationId()).orElse(null);
        }
        // Archived/grouped-away notifications cannot safely reconstruct the historic actor name.
        String body = AuthorPresentation.WITHDRAWN_DISPLAY_NAME + "의 알림입니다.";
        if (notification != null && notification.getUser() != null && notification.getActor() != null
                && Objects.equals(receiverUserId, notification.getUser().getUserId())
                && Objects.equals(actorId, notification.getActor().getUserId())) {
            body = NotificationResponse.NotificationSummary.from(notification).getMessage();
        }
        return payloadFactory.withWithdrawnPresentation(payload, body);
    }

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
