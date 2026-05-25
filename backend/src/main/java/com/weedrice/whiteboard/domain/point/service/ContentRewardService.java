package com.weedrice.whiteboard.domain.point.service;

import com.weedrice.whiteboard.domain.point.repository.PointHistoryRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContentRewardService {

    private static final String EARN_TYPE = "EARN";

    private final PointService pointService;
    private final PointHistoryRepository pointHistoryRepository;
    private final GlobalConfigService globalConfigService;

    @Transactional
    public void rewardCreate(Long userId, Long relatedId, ContentRewardPolicy policy) {
        int createReward = resolveCreateReward(policy);
        if (createReward <= 0) {
            return;
        }
        pointService.addPointIfAbsent(
                userId,
                createReward,
                policy.getCreateDescription(),
                relatedId,
                policy.getRelatedType());
    }

    @Transactional
    public void rollbackCreateReward(User user, Long relatedId, ContentRewardPolicy policy) {
        int rewardedAmount = getCreateRewardAmount(user, relatedId, policy);
        if (rewardedAmount > 0) {
            pointService.reverseRewardPoint(
                    user.getUserId(),
                    rewardedAmount,
                    policy.getDeleteDescription(),
                    relatedId,
                    policy.getRelatedType());
        }
    }

    private int resolveCreateReward(ContentRewardPolicy policy) {
        String createRewardConfig = globalConfigService.getConfig(policy.getConfigKey());
        return GlobalConfigService.parseIntConfigOrDefault(
                createRewardConfig,
                policy.getDefaultCreateReward(),
                0);
    }

    private int getCreateRewardAmount(User user, Long relatedId, ContentRewardPolicy policy) {
        long rewardedAmount = pointHistoryRepository.sumPositiveAmountByUserAndTypeAndRelatedTypeAndRelatedId(
                user,
                EARN_TYPE,
                policy.getRelatedType(),
                relatedId);
        return Math.toIntExact(rewardedAmount);
    }
}
