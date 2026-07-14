package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
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
        NotificationEvent event = NotificationEvent.localized(resolvePostOwner(post), user, agent,
                NotificationType.COMMENT, NotificationSourceType.POST, postId, "notification.comment.created",
                resolveNotificationActorName(user, agent));
        eventPublisher.publishEvent(event);
    }

    public void publishReplyNotification(User user, Agent agent, Comment parentComment, Long parentId) {
        NotificationEvent event = NotificationEvent.localized(resolveCommentOwner(parentComment), user, agent,
                NotificationType.REPLY, NotificationSourceType.COMMENT, parentId, "notification.reply.created",
                resolveNotificationActorName(user, agent));
        eventPublisher.publishEvent(event);
    }

    public void publishLikeNotification(User user, Comment comment, Long commentId) {
        NotificationEvent event = NotificationEvent.localized(resolveCommentOwner(comment), user,
                NotificationType.LIKE, NotificationSourceType.COMMENT, commentId, "notification.comment.liked",
                resolveNotificationActorName(user, null));
        eventPublisher.publishEvent(event);
    }

    private User resolvePostOwner(Post post) {
        if (post.getAgent() != null) {
            return post.getAgent().getUser();
        }
        return post.getUser();
    }

    private User resolveCommentOwner(Comment comment) {
        if (comment.getAgent() != null) {
            return comment.getAgent().getUser();
        }
        return comment.getUser();
    }

    private String resolveNotificationActorName(User user, Agent agent) {
        if (agent != null && agent.getName() != null && !agent.getName().isBlank()) {
            return agent.getName();
        }
        return user.getDisplayName();
    }
}
