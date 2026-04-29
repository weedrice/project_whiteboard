package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.point.repository.PointHistoryRepository;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentRewardServiceTest {

    @Mock
    private PointService pointService;
    @Mock
    private PointHistoryRepository pointHistoryRepository;
    @Mock
    private GlobalConfigService globalConfigService;

    private CommentRewardService commentRewardService;

    @BeforeEach
    void setUp() {
        commentRewardService = new CommentRewardService(
                pointService,
                pointHistoryRepository,
                globalConfigService);
    }

    @Test
    @DisplayName("rewardCreate uses default reward for invalid config")
    void rewardCreate_invalidConfig_usesDefaultReward() {
        when(globalConfigService.getConfig("POINT_COMMENT_CREATE_REWARD")).thenReturn("invalid");

        commentRewardService.rewardCreate(1L, 10L);

        verify(pointService).addPoint(1L, 10, "\uB313\uAE00 \uC791\uC131", 10L, "COMMENT");
    }

    @Test
    @DisplayName("rewardCreate uses default reward below minimum")
    void rewardCreate_belowMinimumConfig_usesDefaultReward() {
        when(globalConfigService.getConfig("POINT_COMMENT_CREATE_REWARD")).thenReturn("-1");

        commentRewardService.rewardCreate(1L, 10L);

        verify(pointService).addPoint(1L, 10, "\uB313\uAE00 \uC791\uC131", 10L, "COMMENT");
    }
}
