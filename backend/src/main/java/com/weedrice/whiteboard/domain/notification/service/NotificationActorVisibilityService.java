package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.actor.query.ContentAuthorVisibilityRepository;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
class NotificationActorVisibilityService {

    private final UserBlockRepository userBlockRepository;
    private final ContentAuthorVisibilityRepository authorVisibilityRepository;

    Set<Long> resolveHiddenActorUserIds(Long receiverUserId, Collection<Notification> notifications) {
        Set<Long> hiddenActorUserIds = new HashSet<>();
        if (notifications == null) {
            return hiddenActorUserIds;
        }
        Set<Long> activeActorUserIds = new LinkedHashSet<>();
        for (Notification notification : notifications) {
            User actor = notification.getActor();
            if (actor == null || actor.getUserId() == null) {
                continue;
            }
            if (isInactive(actor)) {
                hiddenActorUserIds.add(actor.getUserId());
            } else {
                activeActorUserIds.add(actor.getUserId());
            }
        }
        if (!activeActorUserIds.isEmpty()) {
            Set<Long> visibleActorUserIds = authorVisibilityRepository.findVisibleUserIds(activeActorUserIds);
            for (Long actorUserId : activeActorUserIds) {
                if (!visibleActorUserIds.contains(actorUserId)) {
                    hiddenActorUserIds.add(actorUserId);
                }
            }
            List<Long> blockedUserIds = userBlockRepository.findBlockedCandidateUserIdsEitherDirection(
                    receiverUserId, List.copyOf(activeActorUserIds));
            if (blockedUserIds != null) {
                hiddenActorUserIds.addAll(blockedUserIds);
            }
        }
        return hiddenActorUserIds;
    }

    boolean shouldHideActor(Long receiverUserId, Notification notification) {
        if (notification == null || notification.getActor() == null) {
            return false;
        }
        User actor = notification.getActor();
        if (isInactive(actor)) {
            return true;
        }
        if (actor.getUserId() != null
                && !authorVisibilityRepository.findVisibleUserIds(List.of(actor.getUserId()))
                        .contains(actor.getUserId())) {
            return true;
        }
        return receiverUserId != null
                && actor.getUserId() != null
                && !receiverUserId.equals(actor.getUserId())
                && userBlockRepository.existsEitherDirection(receiverUserId, actor.getUserId());
    }

    private boolean isInactive(User actor) {
        return !User.STATUS_ACTIVE.equals(actor.getStatus()) || actor.getDeletedAt() != null;
    }
}
