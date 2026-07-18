package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.agent.service.AgentLifecycleService;
import com.weedrice.whiteboard.domain.auth.service.AccountCredentialInvalidationService;
import com.weedrice.whiteboard.domain.auth.service.RefreshTokenLifecycleService;
import com.weedrice.whiteboard.domain.notification.service.NotificationAccessInvalidationService;
import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserLifecycleService {

    private final UserRepository userRepository;
    private final SanctionRepository sanctionRepository;
    private final RefreshTokenLifecycleService refreshTokenLifecycleService;
    private final AccountCredentialInvalidationService accountCredentialInvalidationService;
    private final AgentLifecycleService agentLifecycleService;
    private final UserPrivilegeCleanupService userPrivilegeCleanupService;
    private final NotificationAccessInvalidationService notificationAccessInvalidationService;
    private final Clock clock;

    @Transactional
    public void updateAdminManagedStatus(Long userId, String status) {
        updateAdminManagedStatus(null, userId, status);
    }

    @Transactional
    public void updateAdminManagedStatus(Long actorUserId, Long userId, String status) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if ("SUSPENDED".equals(status)) {
            suspendUser(user, actorUserId);
            return;
        }
        if ("ACTIVE".equals(status)) {
            if ("DELETED".equals(user.getStatus())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            if (sanctionRepository.existsActiveBan(user, LocalDateTime.now(clock))) {
                throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
            }
            user.activate();
            return;
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Transactional
    public void suspendUser(User user) {
        validateLifecycleMutationTarget(user);
        User lockedUser = userRepository.findByIdForUpdate(user.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        suspendLockedUser(lockedUser, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void suspendPrelockedUser(User user) {
        suspendLockedUser(user, null);
    }

    @Transactional
    public void deleteAccount(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        deletePrelockedAccount(user);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deletePrelockedAccount(User user) {
        validateLifecycleMutationTarget(user);
        cleanupOperationalAccessForLockedUser(user, null);
        accountCredentialInvalidationService.invalidateForLockedUser(user);
        user.delete(LocalDateTime.now(clock));
        user.advanceSecurityVersion();
    }

    private void suspendUser(User user, Long actorUserId) {
        suspendLockedUser(user, actorUserId);
    }

    private void suspendLockedUser(User user, Long actorUserId) {
        validateLifecycleMutationTarget(user);
        cleanupOperationalAccessForLockedUser(user, actorUserId);
        user.suspend();
        user.advanceSecurityVersion();
    }

    private void validateLifecycleMutationTarget(User user) {
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if ("DELETED".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void cleanupOperationalAccessForLockedUser(User user, Long actorUserId) {
        if (actorUserId == null) {
            userPrivilegeCleanupService.removeOperationalPrivileges(user);
        } else {
            userPrivilegeCleanupService.removeOperationalPrivileges(user, actorUserId);
        }
        refreshTokenLifecycleService.revokeActiveRefreshTokensForLockedUser(user);
        notificationAccessInvalidationService.revokeForLockedUser(user.getUserId());
        agentLifecycleService.suspendAllForUser(user);
    }
}
