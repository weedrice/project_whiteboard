package com.weedrice.whiteboard.domain.badge.service;

import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public int evaluateCommentCountBadges(Long userId) {
        User user = activeUser(userId);
        if (user == null) {
            return 0;
        }
        long count = commentRepository.countByUserAndIsDeleted(user, false);
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
}
