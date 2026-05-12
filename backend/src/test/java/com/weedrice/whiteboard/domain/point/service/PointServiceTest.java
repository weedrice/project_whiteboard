package com.weedrice.whiteboard.domain.point.service;

import com.weedrice.whiteboard.domain.point.entity.PointHistory;
import com.weedrice.whiteboard.domain.point.entity.UserPoint;
import com.weedrice.whiteboard.domain.point.repository.PointHistoryRepository;
import com.weedrice.whiteboard.domain.point.repository.UserPointRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private UserPointRepository userPointRepository;
    @Mock
    private PointHistoryRepository pointHistoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SanctionService sanctionService;

    @InjectMocks
    private PointService pointService;

    private User user;
    private UserPoint userPoint;

    @BeforeEach
    void setUp() {
        user = User.builder().build();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "userId", 1L);
        userPoint = UserPoint.builder().user(user).build();
    }

    @Test
    @DisplayName("포인트 적립 성공")
    void addPoint_success() {
        // given
        Long userId = 1L;
        int amount = 100;
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(userPointRepository.findByUserId(userId)).thenReturn(Optional.of(userPoint));

        // when
        pointService.addPoint(userId, amount, "Test Earn", null, null);

        // then
        assertThat(userPoint.getCurrentPoint()).isEqualTo(100);
        verify(userPointRepository).save(any(UserPoint.class));
        verify(pointHistoryRepository).save(any());
    }

    @Test
    @DisplayName("포인트 적립은 0 이하 금액을 거절한다")
    void addPoint_rejectsNonPositiveAmount() {
        for (int amount : invalidAmounts()) {
            assertThatThrownBy(() -> pointService.addPoint(1L, amount, "Invalid Earn", null, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }

        verify(userRepository, never()).findByIdForUpdate(any());
        verify(userPointRepository, never()).save(any());
        verify(pointHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("포인트 적립은 잔액 오버플로를 거절한다")
    void addPoint_rejectsBalanceOverflow() {
        Long userId = 1L;
        userPoint.addPoint(Integer.MAX_VALUE);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(userPointRepository.findByUserId(userId)).thenReturn(Optional.of(userPoint));

        assertThatThrownBy(() -> pointService.addPoint(userId, 1, "Overflow Earn", null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        assertThat(userPoint.getCurrentPoint()).isEqualTo(Integer.MAX_VALUE);
        verify(userPointRepository, never()).save(any());
        verify(pointHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("중복 방지 포인트 적립은 사용자 잠금 후 기존 이력을 확인하고 적립한다")
    void addPointIfAbsent_noHistory_addsPointAfterUserLock() {
        Long userId = 1L;
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(pointHistoryRepository.existsByUser_UserIdAndTypeAndRelatedTypeAndRelatedId(
                userId,
                "EARN",
                "POST",
                10L))
                .thenReturn(false);
        when(userPointRepository.findByUserId(userId)).thenReturn(Optional.of(userPoint));

        pointService.addPointIfAbsent(userId, 100, "Post reward", 10L, "POST");

        assertThat(userPoint.getCurrentPoint()).isEqualTo(100);
        org.mockito.InOrder inOrder = inOrder(userRepository, pointHistoryRepository);
        inOrder.verify(userRepository).findByIdForUpdate(userId);
        inOrder.verify(pointHistoryRepository).existsByUser_UserIdAndTypeAndRelatedTypeAndRelatedId(
                userId,
                "EARN",
                "POST",
                10L);
        verify(userPointRepository).save(any(UserPoint.class));
        verify(pointHistoryRepository).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("중복 방지 포인트 적립은 기존 이력이 있으면 잔액과 이력을 변경하지 않는다")
    void addPointIfAbsent_existingHistory_skipsPointChange() {
        Long userId = 1L;
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(pointHistoryRepository.existsByUser_UserIdAndTypeAndRelatedTypeAndRelatedId(
                userId,
                "EARN",
                "COMMENT",
                20L))
                .thenReturn(true);

        pointService.addPointIfAbsent(userId, 100, "Comment reward", 20L, "COMMENT");

        assertThat(userPoint.getCurrentPoint()).isZero();
        verify(userPointRepository, never()).findByUserId(any());
        verify(userPointRepository, never()).save(any());
        verify(pointHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("포인트 강제 차감 성공")
    void forceSubtractPoint_success() {
        // given
        Long userId = 1L;
        int initialPoint = 200;
        int amount = 100;
        userPoint.addPoint(initialPoint);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(userPointRepository.findByUserId(userId)).thenReturn(Optional.of(userPoint));

        // when
        pointService.forceSubtractPoint(userId, amount, "Test Spend", null, null);

        // then
        assertThat(userPoint.getCurrentPoint()).isEqualTo(initialPoint - amount);
        verify(userPointRepository).save(any(UserPoint.class));
        verify(pointHistoryRepository).save(any());
    }

    @Test
    @DisplayName("포인트 강제 차감은 0 이하 금액을 거절한다")
    void forceSubtractPoint_rejectsNonPositiveAmount() {
        for (int amount : invalidAmounts()) {
            assertThatThrownBy(() -> pointService.forceSubtractPoint(1L, amount, "Invalid Penalty", null, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }

        verify(userRepository, never()).findByIdForUpdate(any());
        verify(userPointRepository, never()).save(any());
        verify(pointHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("포인트 강제 차감은 잔액 언더플로를 거절한다")
    void forceSubtractPoint_rejectsBalanceUnderflow() {
        Long userId = 1L;
        org.springframework.test.util.ReflectionTestUtils.setField(userPoint, "currentPoint", Integer.MIN_VALUE);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(userPointRepository.findByUserId(userId)).thenReturn(Optional.of(userPoint));

        assertThatThrownBy(() -> pointService.forceSubtractPoint(userId, 1, "Overflow Penalty", null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        assertThat(userPoint.getCurrentPoint()).isEqualTo(Integer.MIN_VALUE);
        verify(userPointRepository, never()).save(any());
        verify(pointHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("보상 회수는 REWARD_REVERSAL 이력으로 기록하고 음수 잔액을 허용한다")
    void reverseRewardPoint_recordsRewardReversalAndAllowsNegativeBalance() {
        Long userId = 1L;
        userPoint.addPoint(50);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(userPointRepository.findByUserId(userId)).thenReturn(Optional.of(userPoint));

        pointService.reverseRewardPoint(userId, 100, "Post delete", 10L, "POST");

        org.mockito.ArgumentCaptor<PointHistory> historyCaptor = org.mockito.ArgumentCaptor.forClass(PointHistory.class);
        verify(pointHistoryRepository).save(historyCaptor.capture());
        assertThat(userPoint.getCurrentPoint()).isEqualTo(-50);
        assertThat(historyCaptor.getValue().getType()).isEqualTo("REWARD_REVERSAL");
        assertThat(historyCaptor.getValue().getAmount()).isEqualTo(-100);
        assertThat(historyCaptor.getValue().getBalanceAfter()).isEqualTo(-50);
        assertThat(historyCaptor.getValue().getRelatedId()).isEqualTo(10L);
        assertThat(historyCaptor.getValue().getRelatedType()).isEqualTo("POST");
    }

    @Test
    @DisplayName("보상 회수는 0 이하 금액을 거절한다")
    void reverseRewardPoint_rejectsNonPositiveAmount() {
        for (int amount : invalidAmounts()) {
            assertThatThrownBy(() -> pointService.reverseRewardPoint(1L, amount, "Invalid Reversal", null, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }

        verify(userRepository, never()).findByIdForUpdate(any());
        verify(userPointRepository, never()).save(any());
        verify(pointHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("구매 포인트 차감은 SPEND 이력으로 기록된다")
    void spendPoint_success() {
        Long userId = 1L;
        userPoint.addPoint(300);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(userPointRepository.findByUserId(userId)).thenReturn(Optional.of(userPoint));

        pointService.spendPoint(userId, 120, "Test Spend", 10L, "SHOP_ITEM");

        org.mockito.ArgumentCaptor<PointHistory> historyCaptor = org.mockito.ArgumentCaptor.forClass(PointHistory.class);
        verify(pointHistoryRepository).save(historyCaptor.capture());
        verify(sanctionService).validateNotBanned(user);
        assertThat(userPoint.getCurrentPoint()).isEqualTo(180);
        assertThat(historyCaptor.getValue().getType()).isEqualTo("SPEND");
        assertThat(historyCaptor.getValue().getAmount()).isEqualTo(-120);
        assertThat(historyCaptor.getValue().getRelatedId()).isEqualTo(10L);
        assertThat(historyCaptor.getValue().getRelatedType()).isEqualTo("SHOP_ITEM");
    }

    @Test
    @DisplayName("구매 포인트 차감은 0 이하 금액을 거절한다")
    void spendPoint_rejectsNonPositiveAmount() {
        for (int amount : invalidAmounts()) {
            assertThatThrownBy(() -> pointService.spendPoint(1L, amount, "Invalid Spend", null, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }

        verify(userRepository, never()).findByIdForUpdate(any());
        verify(userPointRepository, never()).save(any());
        verify(pointHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("구매 포인트 차감은 잔액 부족 시 예외를 던진다")
    void spendPoint_insufficientPoints() {
        Long userId = 1L;
        userPoint.addPoint(50);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(userPointRepository.findByUserId(userId)).thenReturn(Optional.of(userPoint));

        assertThatThrownBy(() -> pointService.spendPoint(userId, 120, "Test Spend", 10L, "SHOP_ITEM"))
                .isInstanceOf(com.weedrice.whiteboard.global.exception.BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(com.weedrice.whiteboard.global.exception.ErrorCode.INSUFFICIENT_POINTS);
    }

    @Test
    @DisplayName("구매 포인트 차감은 BAN 사용자에게 예외를 던진다")
    void spendPoint_bannedUser() {
        Long userId = 1L;
        userPoint.addPoint(300);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE))
                .when(sanctionService)
                .validateNotBanned(user);

        assertThatThrownBy(() -> pointService.spendPoint(userId, 120, "Test Spend", 10L, "SHOP_ITEM"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);
    }

    @Test
    @DisplayName("사용자 포인트 조회 성공")
    void getUserPoint_success() {
        // given
        Long userId = 1L;
        userPoint.addPoint(500);
        when(userRepository.existsById(userId)).thenReturn(true);
        when(userPointRepository.findByUserId(userId)).thenReturn(Optional.of(userPoint));

        // when
        com.weedrice.whiteboard.domain.point.dto.UserPointResponse response = pointService.getUserPoint(userId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getCurrentPoint()).isEqualTo(500);
        verify(userPointRepository).findByUserId(userId);
    }

    @Test
    @DisplayName("빈 지갑은 0포인트로 해석한다")
    void getUserPoint_missingWallet_returnsZero() {
        Long userId = 1L;
        when(userRepository.existsById(userId)).thenReturn(true);
        when(userPointRepository.findByUserId(userId)).thenReturn(Optional.empty());

        com.weedrice.whiteboard.domain.point.dto.UserPointResponse response = pointService.getUserPoint(userId);

        assertThat(response.getCurrentPoint()).isZero();
    }

    @Test
    @DisplayName("현재 잔액 조회는 사용자 존재 시 빈 지갑을 0으로 반환한다")
    void getCurrentBalance_missingWallet_returnsZero() {
        Long userId = 1L;
        when(userRepository.existsById(userId)).thenReturn(true);
        when(userPointRepository.findByUserId(userId)).thenReturn(Optional.empty());

        int balance = pointService.getCurrentBalance(userId);

        assertThat(balance).isZero();
    }

    @Test
    @DisplayName("포인트 내역 조회 성공 - 전체")
    void getPointHistories_success_all() {
        // given
        Long userId = 1L;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        com.weedrice.whiteboard.domain.point.entity.PointHistory history = com.weedrice.whiteboard.domain.point.entity.PointHistory.builder()
                .user(user)
                .type("EARN")
                .amount(100)
                .balanceAfter(100)
                .description("Test")
                .build();
        org.springframework.data.domain.Page<com.weedrice.whiteboard.domain.point.entity.PointHistory> historyPage = new org.springframework.data.domain.PageImpl<>(
                java.util.Collections.singletonList(history), pageable, 1);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(pointHistoryRepository.findByUserOrderByCreatedAtDescHistoryIdDesc(user, pageable)).thenReturn(historyPage);

        // when
        com.weedrice.whiteboard.domain.point.dto.PointHistoryResponse response = pointService.getPointHistories(userId, null, pageable);

        // then
        assertThat(response).isNotNull();
        verify(userRepository).findById(userId);
    }

    @Test
    @DisplayName("포인트 내역 조회 성공 - 타입별")
    void getPointHistories_success_byType() {
        // given
        Long userId = 1L;
        String type = "EARN";
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        com.weedrice.whiteboard.domain.point.entity.PointHistory history = com.weedrice.whiteboard.domain.point.entity.PointHistory.builder()
                .user(user)
                .type("EARN")
                .amount(100)
                .balanceAfter(100)
                .description("Test")
                .build();
        org.springframework.data.domain.Page<com.weedrice.whiteboard.domain.point.entity.PointHistory> historyPage = new org.springframework.data.domain.PageImpl<>(
                java.util.Collections.singletonList(history), pageable, 1);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(pointHistoryRepository.findByUserAndTypeOrderByCreatedAtDescHistoryIdDesc(user, type, pageable))
                .thenReturn(historyPage);

        // when
        com.weedrice.whiteboard.domain.point.dto.PointHistoryResponse response = pointService.getPointHistories(userId, type, pageable);

        // then
        assertThat(response).isNotNull();
        verify(pointHistoryRepository).findByUserAndTypeOrderByCreatedAtDescHistoryIdDesc(user, type, pageable);
    }

    @Test
    @DisplayName("포인트 내역 조회는 타입을 정규화한다")
    void getPointHistories_normalizesType() {
        Long userId = 1L;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.springframework.data.domain.Page<PointHistory> historyPage = new org.springframework.data.domain.PageImpl<>(
                java.util.Collections.emptyList(), pageable, 0);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(pointHistoryRepository.findByUserAndTypeOrderByCreatedAtDescHistoryIdDesc(user, "SPEND", pageable))
                .thenReturn(historyPage);

        com.weedrice.whiteboard.domain.point.dto.PointHistoryResponse response =
                pointService.getPointHistories(userId, " spend ", pageable);

        assertThat(response).isNotNull();
        verify(pointHistoryRepository).findByUserAndTypeOrderByCreatedAtDescHistoryIdDesc(user, "SPEND", pageable);
    }

    @Test
    @DisplayName("포인트 내역 조회는 보상 회수 타입을 허용한다")
    void getPointHistories_allowsRewardReversalType() {
        Long userId = 1L;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.springframework.data.domain.Page<PointHistory> historyPage = new org.springframework.data.domain.PageImpl<>(
                java.util.Collections.emptyList(), pageable, 0);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(pointHistoryRepository.findByUserAndTypeOrderByCreatedAtDescHistoryIdDesc(
                user,
                "REWARD_REVERSAL",
                pageable))
                .thenReturn(historyPage);

        com.weedrice.whiteboard.domain.point.dto.PointHistoryResponse response =
                pointService.getPointHistories(userId, " reward_reversal ", pageable);

        assertThat(response).isNotNull();
        verify(pointHistoryRepository).findByUserAndTypeOrderByCreatedAtDescHistoryIdDesc(
                user,
                "REWARD_REVERSAL",
                pageable);
    }

    @Test
    @DisplayName("포인트 내역 조회는 빈 타입을 전체 조회로 처리한다")
    void getPointHistories_blankType_returnsAll() {
        Long userId = 1L;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.springframework.data.domain.Page<PointHistory> historyPage = new org.springframework.data.domain.PageImpl<>(
                java.util.Collections.emptyList(), pageable, 0);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(pointHistoryRepository.findByUserOrderByCreatedAtDescHistoryIdDesc(user, pageable)).thenReturn(historyPage);

        com.weedrice.whiteboard.domain.point.dto.PointHistoryResponse response =
                pointService.getPointHistories(userId, "   ", pageable);

        assertThat(response).isNotNull();
        verify(pointHistoryRepository).findByUserOrderByCreatedAtDescHistoryIdDesc(user, pageable);
        verify(pointHistoryRepository, never()).findByUserAndTypeOrderByCreatedAtDescHistoryIdDesc(any(), any(), any());
    }

    @Test
    @DisplayName("포인트 내역 조회는 지원하지 않는 타입을 거절한다")
    void getPointHistories_invalidType_throwsInvalidInput() {
        Long userId = 1L;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);

        assertThatThrownBy(() -> pointService.getPointHistories(userId, "bonus", pageable))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(pointHistoryRepository, never()).findByUserAndTypeOrderByCreatedAtDescHistoryIdDesc(any(), any(), any());
        verify(pointHistoryRepository, never()).findByUserOrderByCreatedAtDescHistoryIdDesc(any(), any());
    }

    private int[] invalidAmounts() {
        return new int[] {0, -1, Integer.MIN_VALUE};
    }
}
