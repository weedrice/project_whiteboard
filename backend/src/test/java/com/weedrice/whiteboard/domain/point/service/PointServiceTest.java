package com.weedrice.whiteboard.domain.point.service;

import com.weedrice.whiteboard.domain.point.entity.UserPoint;
import com.weedrice.whiteboard.domain.point.repository.PointHistoryRepository;
import com.weedrice.whiteboard.domain.point.repository.UserPointRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    @DisplayName("포인트 차감 성공")
    void subtractPoint_success() {
        // given
        Long userId = 1L;
        int initialPoint = 200;
        int amount = 100;
        userPoint.addPoint(initialPoint);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userPointRepository.findByUserId(userId)).thenReturn(Optional.of(userPoint));

        // when
        pointService.subtractPoint(userId, amount, "Test Spend", null, null);

        // then
        assertThat(userPoint.getCurrentPoint()).isEqualTo(initialPoint - amount);
        verify(userPointRepository).save(any(UserPoint.class));
        verify(pointHistoryRepository).save(any());
    }

    @Test
    @DisplayName("포인트 차감 실패 - 포인트 부족")
    void subtractPoint_fail_insufficientPoints() {
        // given
        Long userId = 1L;
        int amount = 100;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userPointRepository.findByUserId(userId)).thenReturn(Optional.of(userPoint));

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> pointService.subtractPoint(userId, amount, "Test Spend", null, null));
        assertThat(exception.getErrorCode()).isEqualTo(com.weedrice.whiteboard.global.exception.ErrorCode.INSUFFICIENT_POINTS);
    }
}
