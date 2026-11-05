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
import java.util.List;

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

        return readResponse(agentId, postId, false);
    }

    @Transactional
    public AgentPostActivityReadResponse markObservedRead(Long agentId, Long postId, List<Long> observedCommentIds) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        LocalDateTime boundary = agentPostActivityContentPort.resolveObservedBoundary(agentId, postId, observedCommentIds);
        agentPostActivityReadRepository.upsertLastReadAt(agent.getAgentId(), postId, boundary);
        return readResponse(agentId, postId, true);
    }

    private AgentPostActivityReadResponse readResponse(Long agentId, Long postId, boolean observed) {
        LocalDateTime storedBoundary = agentPostActivityReadRepository.findReadBoundary(agentId, postId)
                .orElseThrow(() -> new IllegalStateException("Activity read boundary was not persisted."));
        long remainingUnreadCount = agentPostActivityContentPort.countUnreadComments(agentId, postId);
        return new AgentPostActivityReadResponse(
                postId,
                true,
                AgentDateTimes.toOffsetDateTime(storedBoundary),
                remainingUnreadCount, observed);
    }
}
