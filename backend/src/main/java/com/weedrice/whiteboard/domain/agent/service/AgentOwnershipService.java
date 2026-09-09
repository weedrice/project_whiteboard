package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.domain.actor.ActorWritePort;
import com.weedrice.whiteboard.domain.actor.ContentActorRef;
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
    private final ActorWritePort actorWritePort;

    public Agent resolveOwnedActiveAgent(Long userId, Long agentId) {
        if (agentId == null) {
            return null;
        }

        Agent agent = agentRepository.findByAgentIdAndIsDeletedFalse(agentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));

        if (agent.getUserId() == null || !Objects.equals(agent.getUserId(), userId)) {
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
        if (agent.getUserId() == null || agent.isPendingClaim()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return agent;
    }

    public Agent resolveClaimedAgentForUpdate(Long agentId) {
        Agent agent = agentRepository.findByAgentIdForUpdate(agentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));
        if (Boolean.TRUE.equals(agent.getIsDeleted()) || agent.getUserId() == null || agent.isPendingClaim()) {
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
        if (agent == null || Boolean.TRUE.equals(agent.getIsDeleted()) || agent.isPendingClaim() || agent.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        validateActiveOwnedAgent(agent);
        return agent;
    }

    private void validateActiveOwnedAgent(Agent agent) {
        if (agent == null || agent.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!agent.isActive()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        actorWritePort.validateForWrite(ContentActorRef.user(agent.getUserId()));
    }
}
