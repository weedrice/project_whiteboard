package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminEligibleUserServiceTest {

    @Mock
    private UserRepository userRepository;

    private AdminEligibleUserService adminEligibleUserService;

    @BeforeEach
    void setUp() {
        adminEligibleUserService = new AdminEligibleUserService(userRepository);
    }

    @Test
    @DisplayName("관리자 대상 loginId를 정규화해 조회한다")
    void getActiveUserByLoginId_trimsLoginId() {
        User user = User.builder()
                .loginId("target")
                .password("encoded")
                .email("target@example.com")
                .displayName("target")
                .build();
        when(userRepository.findByLoginId("target")).thenReturn(Optional.of(user));

        User result = adminEligibleUserService.getActiveUserByLoginId(" target ");

        assertThat(result).isSameAs(user);
        verify(userRepository).findByLoginId("target");
    }

    @Test
    @DisplayName("관리자 배정 대상은 쓰기 잠금으로 조회하고 활성 상태를 검증한다")
    void getActiveUserByLoginIdForUpdate_locksNormalizedUser() {
        User user = User.builder()
                .loginId("target")
                .password("encoded")
                .email("target@example.com")
                .displayName("target")
                .build();
        when(userRepository.findByLoginIdForUpdate("target")).thenReturn(Optional.of(user));

        User result = adminEligibleUserService.getActiveUserByLoginIdForUpdate(" target ");

        assertThat(result).isSameAs(user);
        verify(userRepository).findByLoginIdForUpdate("target");
        verify(userRepository, never()).findByLoginId("target");
    }

    @Test
    @DisplayName("관리자 재활성화 대상도 사용자 ID로 쓰기 잠금한다")
    void lockActiveUser_locksCandidateById() {
        User user = User.builder().build();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "userId", 7L);
        when(userRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(user));

        assertThat(adminEligibleUserService.lockActiveUser(user)).isSameAs(user);

        verify(userRepository).findActiveByIdForUpdate(7L);
    }

    @Test
    @DisplayName("잠금 시점에 비활성화된 관리자는 배정 대상에서 제외한다")
    void lockActiveUser_rejectsUserThatBecameInactive() {
        User user = User.builder().build();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "userId", 7L);
        when(userRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.empty());
        when(userRepository.existsById(7L)).thenReturn(true);

        assertThatThrownBy(() -> adminEligibleUserService.lockActiveUser(user))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);
    }

    @Test
    @DisplayName("빈 loginId는 조회 전에 거절한다")
    void getActiveUserByLoginId_rejectsBlankLoginId() {
        assertThatThrownBy(() -> adminEligibleUserService.getActiveUserByLoginId("  "))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(userRepository, never()).findByLoginId(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("너무 긴 loginId는 조회 전에 거절한다")
    void getActiveUserByLoginId_rejectsTooLongLoginId() {
        assertThatThrownBy(() -> adminEligibleUserService.getActiveUserByLoginId("a".repeat(31)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(userRepository, never()).findByLoginId(org.mockito.ArgumentMatchers.any());
    }
}
