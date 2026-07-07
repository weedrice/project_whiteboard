package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.agent.service.AgentLifecycleService;
import com.weedrice.whiteboard.domain.auth.service.RefreshTokenLifecycleService;
import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLifecycleServiceTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 7, 7, 12, 0);

    @InjectMocks
    private UserLifecycleService userLifecycleService;

    @Mock private UserRepository userRepository;
    @Mock private SanctionRepository sanctionRepository;
    @Mock private RefreshTokenLifecycleService refreshTokenLifecycleService;
    @Mock private AgentLifecycleService agentLifecycleService;
    @Mock private UserPrivilegeCleanupService userPrivilegeCleanupService;
    @Mock private Clock clock;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(FIXED_NOW.toInstant(ZoneOffset.UTC));
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("suspend revokes refresh tokens and suspends agents")
    void updateAdminManagedStatus_suspendRevokesSessionsAndAgents() {
        User user = User.builder().build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        userLifecycleService.updateAdminManagedStatus(1L, "SUSPENDED");

        assertThat(user.getStatus()).isEqualTo("SUSPENDED");
        verify(userRepository).findByIdForUpdate(1L);
        verify(userPrivilegeCleanupService).removeOperationalPrivileges(user);
        verify(refreshTokenLifecycleService).revokeActiveRefreshTokensForLockedUser(user);
        verify(agentLifecycleService).suspendAllForUser(user);
    }

    @Test
    @DisplayName("admin-managed suspend passes current admin to privilege cleanup")
    void updateAdminManagedStatus_suspendUsesActorAwarePrivilegeCleanup() {
        User user = User.builder().build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        userLifecycleService.updateAdminManagedStatus(9L, 1L, "SUSPENDED");

        assertThat(user.getStatus()).isEqualTo("SUSPENDED");
        verify(userPrivilegeCleanupService).removeOperationalPrivileges(user, 9L);
        verify(refreshTokenLifecycleService).revokeActiveRefreshTokensForLockedUser(user);
        verify(agentLifecycleService).suspendAllForUser(user);
    }

    @Test
    @DisplayName("public suspendUser locks user before revoking operational access")
    void suspendUser_locksUserBeforeRevokingOperationalAccess() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        userLifecycleService.suspendUser(user);

        assertThat(user.getStatus()).isEqualTo("SUSPENDED");
        var inOrder = inOrder(userRepository, userPrivilegeCleanupService, refreshTokenLifecycleService, agentLifecycleService);
        inOrder.verify(userRepository).findByIdForUpdate(1L);
        inOrder.verify(userPrivilegeCleanupService).removeOperationalPrivileges(user);
        inOrder.verify(refreshTokenLifecycleService).revokeActiveRefreshTokensForLockedUser(user);
        inOrder.verify(agentLifecycleService).suspendAllForUser(user);
    }

    @Test
    @DisplayName("prelocked suspend reuses locked user without repository lookup")
    void suspendPrelockedUser_reusesLockedUserWithoutRepositoryLookup() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        userLifecycleService.suspendPrelockedUser(user);

        assertThat(user.getStatus()).isEqualTo("SUSPENDED");
        verifyNoInteractions(userRepository);
        var inOrder = inOrder(userPrivilegeCleanupService, refreshTokenLifecycleService, agentLifecycleService);
        inOrder.verify(userPrivilegeCleanupService).removeOperationalPrivileges(user);
        inOrder.verify(refreshTokenLifecycleService).revokeActiveRefreshTokensForLockedUser(user);
        inOrder.verify(agentLifecycleService).suspendAllForUser(user);
    }

    @Test
    @DisplayName("suspend does not revoke sessions or agents when operational privilege guard rejects")
    void updateAdminManagedStatus_suspendGuardRejectedDoesNotMutate() {
        User user = User.builder().build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(userPrivilegeCleanupService).removeOperationalPrivileges(user);

        assertThatThrownBy(() -> userLifecycleService.updateAdminManagedStatus(1L, "SUSPENDED"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        assertThat(user.getStatus()).isEqualTo("ACTIVE");
        verify(refreshTokenLifecycleService, never()).revokeActiveRefreshTokensForLockedUser(user);
        verify(agentLifecycleService, never()).suspendAllForUser(user);
    }

    @Test
    @DisplayName("deleteAccount revokes operational access and marks user deleted")
    void deleteAccount_revokesOperationalAccessAndDeletesUser() {
        User user = User.builder().build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        userLifecycleService.deleteAccount(1L);

        assertThat(user.getStatus()).isEqualTo("DELETED");
        verify(userRepository).findByIdForUpdate(1L);
        var inOrder = inOrder(userPrivilegeCleanupService, refreshTokenLifecycleService, agentLifecycleService);
        inOrder.verify(userPrivilegeCleanupService).removeOperationalPrivileges(user);
        inOrder.verify(refreshTokenLifecycleService).revokeActiveRefreshTokensForLockedUser(user);
        inOrder.verify(agentLifecycleService).suspendAllForUser(user);
    }

    @Test
    @DisplayName("deleteAccount does not revoke sessions or agents when operational privilege guard rejects")
    void deleteAccount_guardRejectedDoesNotMutate() {
        User user = User.builder().build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(userPrivilegeCleanupService).removeOperationalPrivileges(user);

        assertThatThrownBy(() -> userLifecycleService.deleteAccount(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        assertThat(user.getStatus()).isEqualTo("ACTIVE");
        verify(refreshTokenLifecycleService, never()).revokeActiveRefreshTokensForLockedUser(user);
        verify(agentLifecycleService, never()).suspendAllForUser(user);
    }

    @Test
    @DisplayName("activate does not automatically restore agents")
    void updateAdminManagedStatus_activateDoesNotRestoreAgents() {
        User user = User.builder().build();
        user.suspend();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(sanctionRepository.existsActiveBan(eq(user), any(LocalDateTime.class)))
                .thenReturn(false);

        userLifecycleService.updateAdminManagedStatus(1L, "ACTIVE");

        assertThat(user.getStatus()).isEqualTo("ACTIVE");
        verify(userRepository).findByIdForUpdate(1L);
        verify(sanctionRepository).existsActiveBan(user, FIXED_NOW);
        verify(refreshTokenLifecycleService, never()).revokeActiveRefreshTokensForLockedUser(user);
        verify(agentLifecycleService, never()).suspendAllForUser(user);
    }

    @Test
    @DisplayName("activate remains blocked for banned users")
    void updateAdminManagedStatus_activeBlockedByBan() {
        User user = User.builder().build();
        user.suspend();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(sanctionRepository.existsActiveBan(eq(user), any(LocalDateTime.class)))
                .thenReturn(true);

        assertThatThrownBy(() -> userLifecycleService.updateAdminManagedStatus(1L, "ACTIVE"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);
        verify(sanctionRepository).existsActiveBan(user, FIXED_NOW);
    }

    @Test
    @DisplayName("deleted users cannot be reactivated")
    void updateAdminManagedStatus_deletedUserCannotBeActivated() {
        User user = User.builder().build();
        user.delete();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userLifecycleService.updateAdminManagedStatus(1L, "ACTIVE"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("deleted users cannot be suspended again")
    void updateAdminManagedStatus_deletedUserCannotBeSuspended() {
        User user = User.builder().build();
        user.delete();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userLifecycleService.updateAdminManagedStatus(1L, "SUSPENDED"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("invalid status is rejected")
    void updateAdminManagedStatus_invalid() {
        User user = User.builder().build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userLifecycleService.updateAdminManagedStatus(1L, "INVALID"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(sanctionRepository, never()).existsActiveBan(eq(user), any(LocalDateTime.class));
    }
}
