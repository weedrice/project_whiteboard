package com.weedrice.whiteboard.domain.badge.service;

import com.weedrice.whiteboard.domain.badge.dto.BadgeBackfillResponse;
import com.weedrice.whiteboard.domain.badge.dto.BadgeCompactResponse;
import com.weedrice.whiteboard.domain.badge.dto.BadgeResponse;
import com.weedrice.whiteboard.domain.badge.entity.Badge;
import com.weedrice.whiteboard.domain.badge.entity.UserBadge;
import com.weedrice.whiteboard.domain.badge.repository.BadgeRepository;
import com.weedrice.whiteboard.domain.badge.repository.UserBadgeRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BadgeService {

    private static final int BACKFILL_PAGE_SIZE = 200;

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final UserRepository userRepository;
    private final UserWritableResolver userWritableResolver;
    private final BadgeEvaluationService badgeEvaluationService;

    public List<BadgeResponse> getUserBadges(Long targetUserId) {
        User user = userRepository.findByUserIdAndStatusAndDeletedAtIsNull(targetUserId, User.STATUS_ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<Badge> badges = badgeRepository.findAllByOrderBySortOrderAscBadgeCodeAsc();
        Map<String, UserBadge> userBadges = userBadgeRepository
                .findByUser_UserIdOrderByBadge_SortOrderAscBadge_BadgeCodeAsc(targetUserId)
                .stream()
                .collect(Collectors.toMap(userBadge -> userBadge.getBadge().getBadgeCode(), Function.identity()));
        return badges.stream()
                .map(badge -> BadgeResponse.from(badge, userBadges.get(badge.getBadgeCode()),
                        user.getRepresentativeBadgeCode()))
                .toList();
    }

    public BadgeCompactResponse getRepresentativeBadge(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, User.STATUS_ACTIVE)
                .map(User::getRepresentativeBadgeCode)
                .flatMap(code -> userBadgeRepository.findByUser_UserIdAndBadge_BadgeCode(userId, code))
                .map(UserBadge::getBadge)
                .map(BadgeCompactResponse::from)
                .orElse(null);
    }

    @Transactional
    public BadgeCompactResponse updateRepresentativeBadge(Long userId, String badgeCode) {
        User user = userWritableResolver.resolveForUpdate(userId);
        String normalizedBadgeCode = badgeCode == null || badgeCode.isBlank() ? null : badgeCode.strip();
        if (normalizedBadgeCode == null) {
            user.updateRepresentativeBadgeCode(null);
            return null;
        }
        UserBadge userBadge = userBadgeRepository.findByUser_UserIdAndBadge_BadgeCode(userId, normalizedBadgeCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        user.updateRepresentativeBadgeCode(normalizedBadgeCode);
        return BadgeCompactResponse.from(userBadge.getBadge());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BadgeBackfillResponse backfillAll() {
        long scannedUsers = 0L;
        long awardedBadges = 0L;
        long lastUserId = 0L;
        while (true) {
            List<User> users = userRepository
                    .findTop200ByUserIdGreaterThanOrderByUserIdAsc(lastUserId);
            if (users.isEmpty()) {
                break;
            }
            List<User> activeUsers = users.stream()
                    .filter(User::isActiveAccount)
                    .toList();
            scannedUsers += activeUsers.size();
            awardedBadges += badgeEvaluationService.evaluateContentCountBadges(activeUsers);
            lastUserId = users.getLast().getUserId();
            if (users.size() < BACKFILL_PAGE_SIZE) {
                break;
            }
        }
        return BadgeBackfillResponse.builder()
                .scannedUsers(scannedUsers)
                .awardedBadges(awardedBadges)
                .build();
    }
}
