package com.weedrice.whiteboard.domain.post.integration;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentOwnershipService;
import com.weedrice.whiteboard.domain.post.port.PostAgentWritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostAgentWriteIntegrationAdapter implements PostAgentWritePort {

    private final AgentOwnershipService agentOwnershipService;

    @Override
    public Long resolveOwnedActiveAgentId(Long ownerUserId, Long requestedAgentId) {
        Agent agent = agentOwnershipService.resolveOwnedActiveAgent(ownerUserId, requestedAgentId);
        return agent == null ? null : agent.getAgentId();
    }
}
