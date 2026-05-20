package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.agent.service.AgentLifecycleService;
import com.weedrice.whiteboard.domain.auth.service.RefreshTokenLifecycleService;
import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserLifecycleService {

    private final UserRepository userRepository;
    private final SanctionRepository sanctionRepository;
    private final RefreshTokenLifecycleService refreshTokenLifecycleService;
    private final AgentLifecycleService agentLifecycleService;
    private final UserPrivilegeCleanupService userPrivilegeCleanupService;

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
            if (sanctionRepository.existsActiveBan(user, java.time.LocalDateTime.now())) {
                throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
            }
            user.activate();
            return;
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Transactional
    public void suspendUser(User user) {
        suspendUser(user, null);
    }

    @Transactional
    public void deleteAccount(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        validateLifecycleMutationTarget(user);
        cleanupOperationalAccess(user, null);
        user.delete();
    }

    private void suspendUser(User user, Long actorUserId) {
        validateLifecycleMutationTarget(user);
        cleanupOperationalAccess(user, actorUserId);
        user.suspend();
    }

    private void validateLifecycleMutationTarget(User user) {
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if ("DELETED".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void cleanupOperationalAccess(User user, Long actorUserId) {
        if (actorUserId == null) {
            userPrivilegeCleanupService.removeOperationalPrivileges(user);
        } else {
            userPrivilegeCleanupService.removeOperationalPrivileges(user, actorUserId);
        }
        refreshTokenLifecycleService.revokeActiveRefreshTokens(user);
        agentLifecycleService.suspendAllForUser(user);
    }
}
