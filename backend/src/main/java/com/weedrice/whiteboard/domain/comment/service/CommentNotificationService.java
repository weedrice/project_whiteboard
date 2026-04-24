package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommentNotificationService {

    private final ApplicationEventPublisher eventPublisher;

    public void publishCreateNotification(User user, Agent agent, Post post, Long postId) {
        if (post.getAgent() != null) {
            return;
        }
        String notificationContent = resolveNotificationActorName(user, agent)
                + "\uB2D8\uC774 \uD68C\uC6D0\uB2D8\uC758 \uAC8C\uC2DC\uAE00\uC5D0 \uB313\uAE00\uC744 \uB0A8\uACBC\uC2B5\uB2C8\uB2E4.";
        NotificationEvent event = new NotificationEvent(post.getUser(), user, agent,
                NotificationType.COMMENT, "POST", postId, notificationContent);
        eventPublisher.publishEvent(event);
    }

    public void publishReplyNotification(User user, Agent agent, Comment parentComment, Long parentId) {
        if (parentComment.getAgent() != null) {
            return;
        }
        String notificationContent = resolveNotificationActorName(user, agent)
                + "\uB2D8\uC774 \uD68C\uC6D0\uB2D8\uC758 \uB313\uAE00\uC5D0 \uB2F5\uAE00\uC744 \uB0A8\uACBC\uC2B5\uB2C8\uB2E4.";
        NotificationEvent event = new NotificationEvent(parentComment.getUser(), user, agent,
                NotificationType.REPLY, "COMMENT", parentId, notificationContent);
        eventPublisher.publishEvent(event);
    }

    public void publishLikeNotification(User user, Comment comment, Long commentId) {
        if (comment.getAgent() != null) {
            return;
        }
        String content = resolveNotificationActorName(user, null)
                + "\uB2D8\uC774 \uD68C\uC6D0\uB2D8\uC758 \uB313\uAE00\uC744 \uC88B\uC544\uD569\uB2C8\uB2E4.";
        NotificationEvent event = new NotificationEvent(comment.getUser(), user, NotificationType.LIKE,
                "COMMENT", commentId, content);
        eventPublisher.publishEvent(event);
    }

    private String resolveNotificationActorName(User user, Agent agent) {
        if (agent != null && agent.getName() != null && !agent.getName().isBlank()) {
            return agent.getName();
        }
        return user.getDisplayName();
    }
}
