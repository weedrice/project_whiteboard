package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentOwnershipService {

    private final AgentRepository agentRepository;
    private final SanctionService sanctionService;

    public Agent resolveOwnedActiveAgent(Long userId, Long agentId) {
        if (agentId == null) {
            return null;
        }

        Agent agent = agentRepository.findByAgentIdAndIsDeletedFalse(agentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));

        if (agent.getUser() == null || !Objects.equals(agent.getUser().getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        validateActiveOwnedAgent(agent);
        return agent;
    }

    public Agent resolveActiveAgent(Long agentId) {
        Agent agent = agentRepository.findByAgentIdAndIsDeletedFalse(agentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));
        validateActiveOwnedAgent(agent);
        return agent;
    }

    public Agent resolveClaimedAgent(Long agentId) {
        Agent agent = agentRepository.findByAgentIdAndIsDeletedFalse(agentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));
        if (agent.getUser() == null || agent.isPendingClaim()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return agent;
    }

    public Agent resolveActiveAgentForUpdate(Long agentId) {
        Agent agent = agentRepository.findByAgentIdForUpdate(agentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));
        validateActiveOwnedAgent(agent);
        return agent;
    }

    public Agent validateAuthenticatedAgent(Agent agent) {
        if (agent == null || Boolean.TRUE.equals(agent.getIsDeleted()) || agent.isPendingClaim() || agent.getUser() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        validateActiveOwnedAgent(agent);
        return agent;
    }

    private void validateActiveOwnedAgent(Agent agent) {
        if (agent == null || agent.getUser() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!agent.isActive()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        validateActiveOwner(agent.getUser());
    }

    private void validateActiveOwner(User user) {
        if (!user.isActiveAccount()) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }
        sanctionService.validateNotBanned(user);
    }
}
