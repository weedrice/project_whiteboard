package com.weedrice.whiteboard.domain.agent.integration;

import com.weedrice.whiteboard.domain.agent.port.AgentOwnerPort;
import com.weedrice.whiteboard.domain.sanction.service.SanctionPolicyService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentOwnerIntegrationAdapter implements AgentOwnerPort {

    private final UserRepository userRepository;
    private final SanctionPolicyService sanctionPolicyService;
    private final EntityManager entityManager;

    @Override
    public AgentOwnerSnapshot resolveActiveOwner(Long userId) {
        return snapshot(validate(userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))));
    }

    @Override
    public AgentOwnerSnapshot resolveActiveOwnerForUpdate(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        return snapshot(validate(user));
    }

    private User validate(User user) {
        if (!user.isActiveAccount()) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }
        sanctionPolicyService.validateNotBanned(user);
        return user;
    }

    private AgentOwnerSnapshot snapshot(User user) {
        return new AgentOwnerSnapshot(user.getUserId(), Boolean.TRUE.equals(user.getIsEmailVerified()));
    }
}
