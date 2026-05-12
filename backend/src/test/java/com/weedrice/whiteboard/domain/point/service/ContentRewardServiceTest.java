package com.weedrice.whiteboard.domain.point.service;

import com.weedrice.whiteboard.domain.point.entity.PointHistory;
import com.weedrice.whiteboard.domain.point.repository.PointHistoryRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentRewardServiceTest {

    @Mock
    private PointService pointService;
    @Mock
    private PointHistoryRepository pointHistoryRepository;
    @Mock
    private GlobalConfigService globalConfigService;

    private ContentRewardService contentRewardService;

    @BeforeEach
    void setUp() {
        contentRewardService = new ContentRewardService(
                pointService,
                pointHistoryRepository,
                globalConfigService);
    }

    @Test
    @DisplayName("rewardCreate uses default comment reward for invalid config")
    void rewardCreate_invalidCommentConfig_usesDefaultReward() {
        when(globalConfigService.getConfig("POINT_COMMENT_CREATE_REWARD")).thenReturn("invalid");

        contentRewardService.rewardCreate(1L, 10L, ContentRewardPolicy.COMMENT);

        verify(pointService).addPointIfAbsent(1L, 10, "댓글 작성", 10L, "COMMENT");
    }

    @Test
    @DisplayName("rewardCreate uses default post reward below minimum")
    void rewardCreate_belowMinimumPostConfig_usesDefaultReward() {
        when(globalConfigService.getConfig("POINT_POST_CREATE_REWARD")).thenReturn("-1");

        contentRewardService.rewardCreate(1L, 100L, ContentRewardPolicy.POST);

        verify(pointService).addPointIfAbsent(1L, 50, "게시글 작성", 100L, "POST");
    }

    @Test
    @DisplayName("rewardCreate skips reward for zero config")
    void rewardCreate_zeroConfig_skipsReward() {
        when(globalConfigService.getConfig("POINT_COMMENT_CREATE_REWARD")).thenReturn("0");

        contentRewardService.rewardCreate(1L, 10L, ContentRewardPolicy.COMMENT);

        verify(pointService, never()).addPointIfAbsent(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("rewardCreate delegates idempotent reward payment")
    void rewardCreate_positiveReward_delegatesIdempotentPayment() {
        when(globalConfigService.getConfig("POINT_POST_CREATE_REWARD")).thenReturn("50");

        contentRewardService.rewardCreate(1L, 100L, ContentRewardPolicy.POST);

        verify(pointService).addPointIfAbsent(1L, 50, "게시글 작성", 100L, "POST");
    }

    @Test
    @DisplayName("rollbackCreateReward subtracts summed positive earn histories")
    void rollbackCreateReward_positiveEarnHistories_subtractsSummedAmount() {
        User user = User.builder().build();
        PointHistory first = PointHistory.builder().amount(10).build();
        PointHistory second = PointHistory.builder().amount(null).build();
        PointHistory third = PointHistory.builder().amount(-5).build();
        PointHistory fourth = PointHistory.builder().amount(15).build();
        when(pointHistoryRepository.findByUserAndTypeAndRelatedTypeAndRelatedIdOrderByCreatedAtAsc(
                user,
                "EARN",
                "COMMENT",
                10L))
                .thenReturn(List.of(first, second, third, fourth));

        org.springframework.test.util.ReflectionTestUtils.setField(user, "userId", 1L);

        contentRewardService.rollbackCreateReward(user, 10L, ContentRewardPolicy.COMMENT);

        verify(pointService).reverseRewardPoint(1L, 25, "댓글 삭제", 10L, "COMMENT");
    }

    @Test
    @DisplayName("rollbackCreateReward skips subtract when no positive earn histories")
    void rollbackCreateReward_noPositiveEarnHistories_skipsSubtract() {
        User user = User.builder().build();
        when(pointHistoryRepository.findByUserAndTypeAndRelatedTypeAndRelatedIdOrderByCreatedAtAsc(
                user,
                "EARN",
                "POST",
                100L))
                .thenReturn(List.of(PointHistory.builder().amount(0).build()));

        org.springframework.test.util.ReflectionTestUtils.setField(user, "userId", 1L);

        contentRewardService.rollbackCreateReward(user, 100L, ContentRewardPolicy.POST);

        verify(pointService, never()).reverseRewardPoint(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }
}
