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

    @Transactional
    public void updateAdminManagedStatus(Long userId, String status) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if ("SUSPENDED".equals(status)) {
            suspendUser(user);
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
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if ("DELETED".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        user.suspend();
        refreshTokenLifecycleService.revokeActiveRefreshTokens(user);
        agentLifecycleService.suspendAllForUser(user);
    }
}
