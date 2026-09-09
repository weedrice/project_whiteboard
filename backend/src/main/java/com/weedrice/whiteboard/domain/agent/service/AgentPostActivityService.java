package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentPostActivityReadResponse;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.repository.AgentPostActivityReadRepository;
import com.weedrice.whiteboard.domain.agent.port.AgentPostActivityContentPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentPostActivityService {

    private final AgentOwnershipService agentOwnershipService;
    private final AgentPostActivityContentPort agentPostActivityContentPort;
    private final AgentPostActivityReadRepository agentPostActivityReadRepository;
    private final Clock clock;

    @Transactional
    public AgentPostActivityReadResponse markRead(Long agentId, Long postId) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        agentPostActivityContentPort.validateOwnedActivePost(agentId, postId);

        LocalDateTime markedAt = AgentDateTimes.now(clock);
        agentPostActivityReadRepository.upsertLastReadAt(agent.getAgentId(), postId, markedAt);

        long remainingUnreadCount = agentPostActivityContentPort.countUnreadComments(agentId, postId);
        return new AgentPostActivityReadResponse(
                postId,
                true,
                AgentDateTimes.toOffsetDateTime(markedAt),
                remainingUnreadCount);
    }
}
