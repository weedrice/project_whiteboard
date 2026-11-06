package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NotificationSourceDeliveryPolicy {
    private final KeywordNotificationDeliveryPolicy keywordPolicy;
    private final MentionNotificationDeliveryPolicy mentionPolicy;

    boolean canDeliver(NotificationDeliveryJob job) {
        return canDeliver(job.getNotificationType() == null ? null : job.getNotificationType().name(),
                job.getSourceType(), job.getSourceId(), job.getActorUserId(), job.getReceiverUserId());
    }

    boolean canDeliver(String type, NotificationSourceType sourceType, Long sourceId, Long actorUserId, Long receiverUserId) {
        if (NotificationType.KEYWORD.name().equalsIgnoreCase(type)) {
            return keywordPolicy.canDeliverKeyword(sourceType, sourceId, actorUserId, receiverUserId);
        }
        if (NotificationType.MENTION.name().equalsIgnoreCase(type)) {
            return mentionPolicy.canDeliverMention(sourceType, sourceId, actorUserId, receiverUserId);
        }
        return true;
    }
}
