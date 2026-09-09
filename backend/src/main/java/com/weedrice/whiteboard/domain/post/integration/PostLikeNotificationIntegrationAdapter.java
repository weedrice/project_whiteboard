package com.weedrice.whiteboard.domain.post.integration;

import com.weedrice.whiteboard.domain.actor.ContentActorRef;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentOwnershipService;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.post.port.PostLikeNotificationPort;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostLikeNotificationIntegrationAdapter implements PostLikeNotificationPort {

    private final UserRepository userRepository;
    private final AgentOwnershipService agentOwnershipService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void publishPostLiked(Long postOwnerUserId, ContentActorRef actor, Long postId) {
        User postOwner = requireUser(postOwnerUserId);
        User actorUser = postOwnerUserId.equals(actor.ownerUserId())
                ? postOwner
                : requireUser(actor.ownerUserId());
        Agent actorAgent = actor.agentId() == null
                ? null
                : agentOwnershipService.resolveOwnedActiveAgent(actor.ownerUserId(), actor.agentId());
        String actorName = actorAgent != null && actorAgent.getName() != null && !actorAgent.getName().isBlank()
                ? actorAgent.getName()
                : actorUser.getDisplayName();
        eventPublisher.publishEvent(NotificationEvent.localized(
                postOwner,
                actorUser,
                actorAgent,
                NotificationType.LIKE,
                NotificationSourceType.POST,
                postId,
                "notification.post.liked",
                actorName));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
