package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
class NotificationActorVisibilityService {

    private final UserBlockRepository userBlockRepository;

    Set<Long> resolveHiddenActorUserIds(Long receiverUserId, Collection<Notification> notifications) {
        List<Long> blockedUserIds = userBlockRepository.findBlockedUserIdsEitherDirectionByUserId(receiverUserId);
        Set<Long> hiddenActorUserIds = blockedUserIds == null
                ? new HashSet<>()
                : new HashSet<>(blockedUserIds);
        if (notifications == null) {
            return hiddenActorUserIds;
        }
        notifications.stream()
                .map(Notification::getActor)
                .filter(Objects::nonNull)
                .filter(this::isInactive)
                .map(User::getUserId)
                .filter(Objects::nonNull)
                .forEach(hiddenActorUserIds::add);
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
        return receiverUserId != null
                && actor.getUserId() != null
                && !receiverUserId.equals(actor.getUserId())
                && userBlockRepository.existsEitherDirection(receiverUserId, actor.getUserId());
    }

    private boolean isInactive(User actor) {
        return !User.STATUS_ACTIVE.equals(actor.getStatus()) || actor.getDeletedAt() != null;
    }
}
