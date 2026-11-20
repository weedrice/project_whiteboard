package com.weedrice.whiteboard.domain.badge.service;

import com.weedrice.whiteboard.domain.badge.repository.UserBadgeRepository;
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
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BadgeEvaluationService {

    private record Milestone(BadgeCode code, int threshold) {}

    private static final List<Milestone> POST_MILESTONES = List.of(
            new Milestone(BadgeCode.FIRST_POST, 1), new Milestone(BadgeCode.POSTS_10, 10),
            new Milestone(BadgeCode.POSTS_100, 100));
    private static final List<Milestone> COMMENT_MILESTONES = List.of(
            new Milestone(BadgeCode.FIRST_COMMENT, 1), new Milestone(BadgeCode.COMMENTS_10, 10),
            new Milestone(BadgeCode.COMMENTS_100, 100));

    private final BadgeAwardService badgeAwardService;
    private final UserBadgeRepository userBadgeRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    @Transactional
    public int evaluatePostCountBadges(Long userId) {
        return evaluateCountBadges(userId, POST_MILESTONES,
                threshold -> postRepository.countActiveByUserIdUpTo(userId, threshold));
    }

    @Transactional
    public int evaluateCommentCountBadges(Long userId) {
        return evaluateCountBadges(userId, COMMENT_MILESTONES,
                threshold -> commentRepository.countActiveByUserIdUpTo(userId, threshold));
    }

    private int evaluateCountBadges(Long userId, List<Milestone> milestones, IntUnaryOperator countUpTo) {
        if (userId == null) {
            return 0;
        }
        Set<String> owned = userBadgeRepository.findOwnedBadgeCodes(userId,
                milestones.stream().map(milestone -> milestone.code().name()).toList());
        List<Milestone> missing = milestones.stream().filter(milestone -> !owned.contains(milestone.code().name()))
                .toList();
        if (missing.isEmpty()) {
            return 0;
        }
        User user = userRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, User.STATUS_ACTIVE).orElse(null);
        if (user == null) {
            return 0;
        }
        int count = countUpTo.applyAsInt(missing.stream().mapToInt(Milestone::threshold).max().orElseThrow());
        int awarded = 0;
        for (Milestone milestone : missing) {
            if (count >= milestone.threshold() && badgeAwardService.awardForActiveUser(user, milestone.code().name())) {
                awarded++;
            }
        }
        return awarded;
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
            addEligibleBadges(eligibleBadges, POST_MILESTONES, postCounts.getOrDefault(userId, 0L));
            addEligibleBadges(eligibleBadges, COMMENT_MILESTONES, commentCounts.getOrDefault(userId, 0L));
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

    private void addEligibleBadges(Set<BadgeCode> badges, List<Milestone> milestones, long count) {
        for (Milestone milestone : milestones) {
            if (count >= milestone.threshold()) {
                badges.add(milestone.code());
            }
        }
    }
}
