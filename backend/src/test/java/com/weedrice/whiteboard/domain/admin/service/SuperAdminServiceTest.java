package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.SuperAdminResponse;
import com.weedrice.whiteboard.domain.admin.dto.SuperAdminUpdateResponse;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OperationalPrivilegeRevocationGuard privilegeRevocationGuard;

    @InjectMocks
    private SuperAdminService superAdminService;

    @Test
    @DisplayName("createSuperAdmin grants role to active user")
    void createSuperAdmin_success() {
        User user = activeUser("target");
        when(userRepository.findByLoginId("target")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SuperAdminUpdateResponse response = superAdminService.createSuperAdmin(" target ");

        assertThat(response.isSuperAdmin()).isTrue();
        assertThat(response.getLoginId()).isEqualTo("target");
    }

    @Test
    @DisplayName("createSuperAdmin rejects blank loginId")
    void createSuperAdmin_blankLoginId_invalidInput() {
        assertThatThrownBy(() -> superAdminService.createSuperAdmin("   "))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verify(userRepository, never()).findByLoginId(any());
    }

    @Test
    @DisplayName("createSuperAdmin rejects suspended user")
    void createSuperAdmin_rejectsSuspendedUser() {
        User user = activeUser("target");
        user.suspend();
        when(userRepository.findByLoginId("target")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> superAdminService.createSuperAdmin("target"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.USER_NOT_ACTIVE));
    }

    @Test
    @DisplayName("createSuperAdmin rejects deleted user")
    void createSuperAdmin_rejectsDeletedUser() {
        User user = activeUser("target");
        user.delete(java.time.LocalDateTime.of(2026, 7, 7, 12, 0));
        when(userRepository.findByLoginId("target")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> superAdminService.createSuperAdmin("target"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.USER_NOT_ACTIVE));
    }

    @Test
    @DisplayName("deactivateSuperAdmin revokes role when another usable super admin exists")
    void deactivateSuperAdmin_success() {
        User target = activeSuperAdmin("target");
        setUserId(target, 1L);
        when(userRepository.findByLoginId("target")).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SuperAdminUpdateResponse response = superAdminService.deactivateSuperAdmin(" target ", 2L);

        assertThat(response.isSuperAdmin()).isFalse();
        assertThat(response.getLoginId()).isEqualTo("target");
        verify(privilegeRevocationGuard).validateSuperAdminCanBeRevokedBy(target, 2L);
    }

    @Test
    @DisplayName("deactivateSuperAdmin rejects missing actor user id")
    void deactivateSuperAdmin_missingActor_unauthorized() {
        assertThatThrownBy(() -> superAdminService.deactivateSuperAdmin("target", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(userRepository, never()).findByLoginId(any());
        verify(privilegeRevocationGuard, never()).validateSuperAdminCanBeRevokedBy(any(), any());
    }

    @Test
    @DisplayName("deactivateSuperAdmin blocks removing the last usable super admin")
    void deactivateSuperAdmin_lastUsableAdmin_forbidden() {
        User target = activeSuperAdmin("target");
        setUserId(target, 1L);
        when(userRepository.findByLoginId("target")).thenReturn(Optional.of(target));
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(privilegeRevocationGuard)
                .validateSuperAdminCanBeRevokedBy(target, 2L);

        assertThatThrownBy(() -> superAdminService.deactivateSuperAdmin("target", 2L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("deactivateSuperAdmin allows revoking unusable super admin without usable-count guard")
    void deactivateSuperAdmin_allowsRevokingUnusableSuperAdmin() {
        User suspendedTarget = activeSuperAdmin("target");
        setUserId(suspendedTarget, 1L);
        suspendedTarget.suspend();
        when(userRepository.findByLoginId("target")).thenReturn(Optional.of(suspendedTarget));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SuperAdminUpdateResponse response = superAdminService.deactivateSuperAdmin("target", 2L);

        assertThat(response.isSuperAdmin()).isFalse();
        verify(privilegeRevocationGuard).validateSuperAdminCanBeRevokedBy(suspendedTarget, 2L);
    }

    @Test
    @DisplayName("deactivateSuperAdmin blocks self revocation")
    void deactivateSuperAdmin_self_forbidden() {
        User target = activeSuperAdmin("target");
        setUserId(target, 1L);
        when(userRepository.findByLoginId("target")).thenReturn(Optional.of(target));
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(privilegeRevocationGuard)
                .validateSuperAdminCanBeRevokedBy(target, 1L);

        assertThatThrownBy(() -> superAdminService.deactivateSuperAdmin("target", 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("getSuperAdmin returns only usable super admins")
    void getSuperAdmin_returnsOnlyUsableSuperAdmins() {
        User usable = activeSuperAdmin("usable");
        when(userRepository.findUsableSuperAdmins()).thenReturn(List.of(usable));

        List<SuperAdminResponse> superAdmins = superAdminService.getSuperAdmin();

        assertThat(superAdmins).hasSize(1);
        assertThat(superAdmins.get(0).getLoginId()).isEqualTo("usable");
        verify(userRepository).findUsableSuperAdmins();
        verify(userRepository, never()).findByIsSuperAdminTrueAndDeletedAtIsNull();
    }

    private User activeUser(String loginId) {
        return User.builder()
                .loginId(loginId)
                .password("password")
                .email(loginId + "@test.com")
                .displayName(loginId)
                .build();
    }

    private User activeSuperAdmin(String loginId) {
        User user = activeUser(loginId);
        user.grantSuperAdminRole();
        return user;
    }

    private void setUserId(User user, Long userId) {
        ReflectionTestUtils.setField(user, "userId", userId);
    }
}
