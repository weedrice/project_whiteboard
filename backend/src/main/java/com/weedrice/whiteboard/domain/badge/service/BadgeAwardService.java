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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

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

        UserBadge userBadge = UserBadge.builder()
                .user(user)
                .badge(badge)
                .acquiredAt(LocalDateTime.now(clock))
                .build();
        try {
            userBadge = userBadgeRepository.saveAndFlush(userBadge);
        } catch (DataIntegrityViolationException exception) {
            return false;
        }

        publishAwardNotification(user, userBadge);
        return true;
    }

    boolean awardIfMissing(Long userId, BadgeCode badgeCode) {
        return awardIfMissing(userId, badgeCode.name());
    }

    private void publishAwardNotification(User user, UserBadge userBadge) {
        String content = "New badge acquired: " + userBadge.getBadge().getName();
        eventPublisher.publishEvent(new NotificationEvent(
                user,
                null,
                NotificationType.BADGE,
                NotificationSourceType.SYSTEM,
                userBadge.getUserBadgeId(),
                content));
    }
}
