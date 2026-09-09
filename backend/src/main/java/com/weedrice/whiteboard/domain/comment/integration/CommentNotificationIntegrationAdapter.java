package com.weedrice.whiteboard.domain.comment.integration;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.domain.comment.port.CommentNotificationPort;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommentNotificationIntegrationAdapter implements CommentNotificationPort {

    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final AgentRepository agentRepository;

    @Override
    public void publishCreate(Long actorUserId, Long actorAgentId, Long postOwnerUserId, Long postId) {
        publish(postOwnerUserId, actorUserId, actorAgentId, NotificationType.COMMENT,
                NotificationSourceType.POST, postId, "notification.comment.created");
    }

    @Override
    public void publishReply(Long actorUserId, Long actorAgentId, Long parentOwnerUserId, Long parentCommentId) {
        publish(parentOwnerUserId, actorUserId, actorAgentId, NotificationType.REPLY,
                NotificationSourceType.COMMENT, parentCommentId, "notification.reply.created");
    }

    @Override
    public void publishLike(Long actorUserId, Long commentOwnerUserId, Long commentId) {
        publish(commentOwnerUserId, actorUserId, null, NotificationType.LIKE,
                NotificationSourceType.COMMENT, commentId, "notification.comment.liked");
    }

    private void publish(Long recipientUserId, Long actorUserId, Long actorAgentId, NotificationType type,
            NotificationSourceType sourceType, Long sourceId, String messageKey) {
        User recipient = userRepository.findById(recipientUserId).orElse(null);
        User actor = userRepository.findById(actorUserId).orElse(null);
        if (recipient == null || actor == null) {
            return;
        }
        Agent actorAgent = actorAgentId == null ? null : agentRepository.findById(actorAgentId).orElse(null);
        String actorName = actorAgent != null && actorAgent.getName() != null && !actorAgent.getName().isBlank()
                ? actorAgent.getName() : actor.getDisplayName();
        eventPublisher.publishEvent(NotificationEvent.localized(
                recipient, actor, actorAgent, type, sourceType, sourceId, messageKey, actorName));
    }
}
