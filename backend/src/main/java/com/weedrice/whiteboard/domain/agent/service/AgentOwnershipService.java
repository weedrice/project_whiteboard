package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AgentOwnershipService {

    private final AgentRepository agentRepository;

    public Agent resolveOwnedActiveAgent(Long userId, Long agentId) {
        if (agentId == null) {
            return null;
        }

        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));

        if (agent.getIsDeleted()
                || !agent.isActive()
                || agent.getUser() == null
                || !Objects.equals(agent.getUser().getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return agent;
    }
}
