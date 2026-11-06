package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class MentionNotificationDeliveryPolicy {
    private final MentionNotificationReadPolicy readPolicy;
    private final UserRepository users;

    boolean canDeliverMention(NotificationSourceType sourceType, Long sourceId, Long actorUserId, Long receiverUserId) {
        if (receiverUserId == null) {
            return false;
        }
        Post source = readPolicy.resolveSourcePost(sourceType, sourceId, actorUserId);
        return source != null && !readPolicy.readableRecipients(source, actorUserId,
                users.findAllById(List.of(receiverUserId))).isEmpty();
    }
}
