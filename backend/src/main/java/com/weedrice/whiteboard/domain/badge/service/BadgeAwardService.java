package com.weedrice.whiteboard.domain.badge.service;

import com.weedrice.whiteboard.domain.badge.entity.Badge;
import com.weedrice.whiteboard.domain.badge.entity.UserBadge;
import com.weedrice.whiteboard.domain.badge.repository.BadgeRepository;
import com.weedrice.whiteboard.domain.badge.repository.UserBadgeRepository;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BadgeAwardService {

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public boolean awardIfMissing(Long userId, String badgeCode) {
        if (userId == null || badgeCode == null || badgeCode.isBlank()) {
            return false;
        }
        if (userBadgeRepository.existsByUser_UserIdAndBadge_BadgeCode(userId, badgeCode)) {
            return false;
        }

        User user = userRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, User.STATUS_ACTIVE)
                .orElse(null);
        if (user == null) {
            return false;
        }
        Badge badge = badgeRepository.findById(badgeCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        return award(user, badge);
    }

    @Transactional
    public int awardMissingContentBadges(
            Map<Long, User> usersById,
            Map<Long, Set<BadgeCode>> eligibleBadgesByUserId) {
        if (usersById == null || usersById.isEmpty()
                || eligibleBadgesByUserId == null || eligibleBadgesByUserId.isEmpty()) {
            return 0;
        }

        Set<String> badgeCodes = eligibleBadgesByUserId.values().stream()
                .flatMap(Set::stream)
                .map(Enum::name)
                .collect(Collectors.toSet());
        if (badgeCodes.isEmpty()) {
            return 0;
        }

        Set<UserBadgeKey> awardedKeys = userBadgeRepository
                .findByUser_UserIdInAndBadge_BadgeCodeIn(usersById.keySet(), badgeCodes)
                .stream()
                .map(userBadge -> new UserBadgeKey(
                        userBadge.getUser().getUserId(),
                        userBadge.getBadge().getBadgeCode()))
                .collect(Collectors.toCollection(HashSet::new));
        Map<String, Badge> badgesByCode = badgeRepository.findAllById(badgeCodes).stream()
                .collect(Collectors.toMap(Badge::getBadgeCode, Function.identity()));

        int awarded = 0;
        for (Map.Entry<Long, Set<BadgeCode>> entry : eligibleBadgesByUserId.entrySet()) {
            User user = usersById.get(entry.getKey());
            if (user == null || !user.isActiveAccount()) {
                continue;
            }
            for (BadgeCode badgeCode : entry.getValue()) {
                UserBadgeKey key = new UserBadgeKey(entry.getKey(), badgeCode.name());
                if (!awardedKeys.add(key)) {
                    continue;
                }
                Badge badge = badgesByCode.get(badgeCode.name());
                if (badge == null) {
                    throw new BusinessException(ErrorCode.NOT_FOUND);
                }
                if (award(user, badge)) {
                    awarded++;
                }
            }
        }
        return awarded;
    }

    private boolean award(User user, Badge badge) {
        int inserted = userBadgeRepository.insertIgnore(
                user.getUserId(),
                badge.getBadgeCode(),
                LocalDateTime.now(clock));
        if (inserted == 0) {
            return false;
        }

        UserBadge userBadge = userBadgeRepository
                .findByUser_UserIdAndBadge_BadgeCode(user.getUserId(), badge.getBadgeCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        publishAwardNotification(user, userBadge);
        return true;
    }

    boolean awardIfMissing(Long userId, BadgeCode badgeCode) {
        return awardIfMissing(userId, badgeCode.name());
    }

    private void publishAwardNotification(User user, UserBadge userBadge) {
        eventPublisher.publishEvent(NotificationEvent.localized(
                user,
                null,
                NotificationType.BADGE,
                NotificationSourceType.SYSTEM,
                userBadge.getUserBadgeId(),
                "notification.badge.awarded",
                userBadge.getBadge().getName()));
    }

    private record UserBadgeKey(Long userId, String badgeCode) {
    }
}
