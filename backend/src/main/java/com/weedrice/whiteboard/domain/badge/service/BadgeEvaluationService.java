package com.weedrice.whiteboard.domain.badge.service;

import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BadgeEvaluationService {

    private final BadgeAwardService badgeAwardService;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    @Transactional
    public int evaluatePostCountBadges(Long userId) {
        User user = activeUser(userId);
        if (user == null) {
            return 0;
        }
        long count = postRepository.countByUserAndIsDeleted(user, false);
        return awardPostCountBadges(userId, count);
    }

    @Transactional
    public int evaluateCommentCountBadges(Long userId) {
        User user = activeUser(userId);
        if (user == null) {
            return 0;
        }
        long count = commentRepository.countByUserAndIsDeleted(user, false);
        return awardCommentCountBadges(userId, count);
    }

    @Transactional
    public int evaluateContentCountBadges(List<User> activeUsers) {
        if (activeUsers == null || activeUsers.isEmpty()) {
            return 0;
        }

        Map<Long, User> usersById = activeUsers.stream()
                .filter(User::isActiveAccount)
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
        if (usersById.isEmpty()) {
            return 0;
        }

        Map<Long, Long> postCounts = postRepository.countActiveByUserIds(usersById.keySet()).stream()
                .collect(Collectors.toMap(
                        PostRepository.UserPostCountProjection::getUserId,
                        PostRepository.UserPostCountProjection::getPostCount));
        Map<Long, Long> commentCounts = commentRepository.countActiveByUserIds(usersById.keySet()).stream()
                .collect(Collectors.toMap(
                        CommentRepository.UserCommentCountProjection::getUserId,
                        CommentRepository.UserCommentCountProjection::getCommentCount));

        Map<Long, Set<BadgeCode>> eligibleBadgesByUserId = new HashMap<>();
        for (Long userId : usersById.keySet()) {
            Set<BadgeCode> eligibleBadges = EnumSet.noneOf(BadgeCode.class);
            addEligiblePostCountBadges(eligibleBadges, postCounts.getOrDefault(userId, 0L));
            addEligibleCommentCountBadges(eligibleBadges, commentCounts.getOrDefault(userId, 0L));
            if (!eligibleBadges.isEmpty()) {
                eligibleBadgesByUserId.put(userId, eligibleBadges);
            }
        }
        return badgeAwardService.awardMissingContentBadges(usersById, eligibleBadgesByUserId);
    }

    @Transactional
    public int evaluateAttendanceStreakBadges(Long userId, int streakCount) {
        int awarded = 0;
        if (streakCount >= 7 && badgeAwardService.awardIfMissing(userId, BadgeCode.ATTENDANCE_7)) {
            awarded++;
        }
        if (streakCount >= 30 && badgeAwardService.awardIfMissing(userId, BadgeCode.ATTENDANCE_30)) {
            awarded++;
        }
        return awarded;
    }

    @Transactional
    public int evaluatePopularPostBadges(Long postOwnerUserId, int likeCount) {
        int awarded = 0;
        if (likeCount >= 10 && badgeAwardService.awardIfMissing(postOwnerUserId, BadgeCode.POPULAR_POST_10)) {
            awarded++;
        }
        if (likeCount >= 50 && badgeAwardService.awardIfMissing(postOwnerUserId, BadgeCode.POPULAR_POST_50)) {
            awarded++;
        }
        return awarded;
    }

    private User activeUser(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, User.STATUS_ACTIVE).orElse(null);
    }

    private int awardPostCountBadges(Long userId, long count) {
        int awarded = 0;
        if (count >= 1 && badgeAwardService.awardIfMissing(userId, BadgeCode.FIRST_POST)) {
            awarded++;
        }
        if (count >= 10 && badgeAwardService.awardIfMissing(userId, BadgeCode.POSTS_10)) {
            awarded++;
        }
        if (count >= 100 && badgeAwardService.awardIfMissing(userId, BadgeCode.POSTS_100)) {
            awarded++;
        }
        return awarded;
    }

    private int awardCommentCountBadges(Long userId, long count) {
        int awarded = 0;
        if (count >= 1 && badgeAwardService.awardIfMissing(userId, BadgeCode.FIRST_COMMENT)) {
            awarded++;
        }
        if (count >= 10 && badgeAwardService.awardIfMissing(userId, BadgeCode.COMMENTS_10)) {
            awarded++;
        }
        if (count >= 100 && badgeAwardService.awardIfMissing(userId, BadgeCode.COMMENTS_100)) {
            awarded++;
        }
        return awarded;
    }

    private void addEligiblePostCountBadges(Set<BadgeCode> badges, long count) {
        if (count >= 1) {
            badges.add(BadgeCode.FIRST_POST);
        }
        if (count >= 10) {
            badges.add(BadgeCode.POSTS_10);
        }
        if (count >= 100) {
            badges.add(BadgeCode.POSTS_100);
        }
    }

    private void addEligibleCommentCountBadges(Set<BadgeCode> badges, long count) {
        if (count >= 1) {
            badges.add(BadgeCode.FIRST_COMMENT);
        }
        if (count >= 10) {
            badges.add(BadgeCode.COMMENTS_10);
        }
        if (count >= 100) {
            badges.add(BadgeCode.COMMENTS_100);
        }
    }
}
