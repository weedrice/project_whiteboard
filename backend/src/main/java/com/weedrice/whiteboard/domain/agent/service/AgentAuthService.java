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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentAuthService {

    private final AgentRepository agentRepository;
    private final AgentLastUsedCommandService agentLastUsedCommandService;
    private final ActorWritePort actorWritePort;

    public Agent authenticate(String rawToken) {
        Agent agent = agentRepository
                .findByAgentTokenHashAndIsDeletedFalseForAuthentication(AgentTokenHasher.hash(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (agent.isPendingClaim() || agent.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!agent.isActive()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        actorWritePort.validateForWrite(ContentActorRef.user(agent.getUserId()));
        agentLastUsedCommandService.markLastUsedIfStale(agent.getAgentId());
        return agent;
    }

}
