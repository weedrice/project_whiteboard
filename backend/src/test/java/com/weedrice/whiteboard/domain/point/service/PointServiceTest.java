package com.weedrice.whiteboard.domain.point.service;

import com.weedrice.whiteboard.domain.point.entity.UserPoint;
import com.weedrice.whiteboard.domain.point.repository.PointHistoryRepository;
import com.weedrice.whiteboard.domain.point.repository.UserPointRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private PointService pointService;

    private User user;
    private UserPoint userPoint;

    @BeforeEach
    void setUp() {
        user = User.builder().build();
        userPoint = UserPoint.builder().user(user).build();
    }

    @Test
    @DisplayName("포인트 적립 성공")
    void addPoint_success() {
        // given
        Long userId = 1L;
        int amount = 100;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userPointRepository.findByUserId(userId)).thenReturn(Optional.of(userPoint));

        // when
        pointService.addPoint(userId, amount, "Test Earn", null, null);

        // then
        assertThat(userPoint.getCurrentPoint()).isEqualTo(100);
        verify(userPointRepository).save(any(UserPoint.class));
        verify(pointHistoryRepository).save(any());
    }

    @Test
    @DisplayName("포인트 강제 차감 성공")
    void forceSubtractPoint_success() {
        // given
        Long userId = 1L;
        int initialPoint = 200;
        int amount = 100;
        userPoint.addPoint(initialPoint);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userPointRepository.findByUserId(userId)).thenReturn(Optional.of(userPoint));

        // when
        pointService.forceSubtractPoint(userId, amount, "Test Spend", null, null);

        // then
        assertThat(userPoint.getCurrentPoint()).isEqualTo(initialPoint - amount);
        verify(userPointRepository).save(any(UserPoint.class));
        verify(pointHistoryRepository).save(any());
    }

    @Test
    @DisplayName("사용자 포인트 조회 성공")
    void getUserPoint_success() {
        // given
        Long userId = 1L;
        userPoint.addPoint(500);
        when(userPointRepository.findByUserId(userId)).thenReturn(Optional.of(userPoint));

        // when
        com.weedrice.whiteboard.domain.point.dto.UserPointResponse response = pointService.getUserPoint(userId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getCurrentPoint()).isEqualTo(500);
        verify(userPointRepository).findByUserId(userId);
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
        when(pointHistoryRepository.findByUserOrderByCreatedAtDesc(user, pageable)).thenReturn(historyPage);

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
        when(pointHistoryRepository.findByUserAndTypeOrderByCreatedAtDesc(user, type, pageable)).thenReturn(historyPage);

        // when
        com.weedrice.whiteboard.domain.point.dto.PointHistoryResponse response = pointService.getPointHistories(userId, type, pageable);

        // then
        assertThat(response).isNotNull();
        verify(pointHistoryRepository).findByUserAndTypeOrderByCreatedAtDesc(user, type, pageable);
    }
}
