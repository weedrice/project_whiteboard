package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.repository.PushDeliveryJobRepository;
import com.weedrice.whiteboard.domain.notification.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationAccessInvalidationService {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PushDeliveryJobRepository pushDeliveryJobRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeForLockedUser(Long userId) {
        pushDeliveryJobRepository.redactForReceiver(userId);
        pushSubscriptionRepository.deleteAllByUserId(userId);
        eventPublisher.publishEvent(NotificationStreamInvalidationEvent.disconnectUser(userId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void disconnectSessionFamilyAfterCommit(Long userId, UUID sessionFamilyId) {
        if (userId != null && sessionFamilyId != null) {
            eventPublisher.publishEvent(
                    NotificationStreamInvalidationEvent.disconnectSessionFamily(userId, sessionFamilyId));
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void disconnectOtherSessionFamiliesAfterCommit(Long userId, UUID retainedSessionFamilyId) {
        if (userId != null) {
            eventPublisher.publishEvent(NotificationStreamInvalidationEvent.disconnectOtherSessionFamilies(
                    userId,
                    retainedSessionFamilyId));
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void invalidateCommentTopicAfterCommit(Long postId) {
        eventPublisher.publishEvent(NotificationStreamInvalidationEvent.invalidatePost(postId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void invalidateCommentTopicsForBoardAfterCommit(Long boardId) {
        eventPublisher.publishEvent(NotificationStreamInvalidationEvent.invalidateBoard(boardId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void invalidateCommentTopicsForUsersAfterCommit(Long... userIds) {
        if (userIds == null) {
            return;
        }
        for (Long userId : userIds) {
            if (userId != null) {
                eventPublisher.publishEvent(NotificationStreamInvalidationEvent.invalidateUserTopics(userId));
            }
        }
    }
}
