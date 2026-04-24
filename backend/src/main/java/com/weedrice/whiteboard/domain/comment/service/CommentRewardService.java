package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.point.entity.PointHistory;
import com.weedrice.whiteboard.domain.point.repository.PointHistoryRepository;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommentRewardService {

    private final PointService pointService;
    private final PointHistoryRepository pointHistoryRepository;
    private final GlobalConfigService globalConfigService;

    public void rewardCreate(Long userId, Long commentId) {
        String commentCreateRewardStr = globalConfigService.getConfig("POINT_COMMENT_CREATE_REWARD");
        int commentCreateReward = commentCreateRewardStr != null ? Integer.parseInt(commentCreateRewardStr) : 10;
        pointService.addPoint(userId, commentCreateReward, "\uB313\uAE00 \uC791\uC131", commentId, "COMMENT");
    }

    public void rollbackCreateReward(Long userId, User user, Long commentId) {
        int rewardedAmount = getCommentCreateRewardAmount(user, commentId);
        if (rewardedAmount > 0) {
            pointService.forceSubtractPoint(userId, rewardedAmount, "\uB313\uAE00 \uC0AD\uC81C", commentId, "COMMENT");
        }
    }

    private int getCommentCreateRewardAmount(User user, Long commentId) {
        return pointHistoryRepository.findByUserAndTypeAndRelatedTypeAndRelatedIdOrderByCreatedAtAsc(
                        user,
                        "EARN",
                        "COMMENT",
                        commentId)
                .stream()
                .map(PointHistory::getAmount)
                .filter(amount -> amount != null && amount > 0)
                .mapToInt(Integer::intValue)
                .sum();
    }
}
