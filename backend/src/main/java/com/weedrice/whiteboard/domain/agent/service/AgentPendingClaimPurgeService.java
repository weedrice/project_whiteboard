package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.config.AgentCleanupProperties;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentPendingClaimPurgeService {

    private final AgentRepository agentRepository;
    private final AgentCleanupProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public long countEligible() {
        return agentRepository.countPendingClaimsEligibleForPurge(
                Agent.STATUS_PENDING_CLAIM,
                purgeBefore());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeNextBatch() {
        LocalDateTime purgeBefore = purgeBefore();
        List<Long> candidateIds = agentRepository.findPendingClaimPurgeCandidateIds(
                Agent.STATUS_PENDING_CLAIM,
                purgeBefore,
                PageRequest.of(0, properties.getPendingClaimPurgeBatchSize()));
        if (candidateIds.isEmpty()) {
            return 0;
        }
        return agentRepository.hardDeleteEligiblePendingClaims(
                candidateIds,
                Agent.STATUS_PENDING_CLAIM,
                purgeBefore);
    }

    private LocalDateTime purgeBefore() {
        return LocalDateTime.now(clock).minusDays(properties.getPendingClaimHardDeleteDays());
    }
}
