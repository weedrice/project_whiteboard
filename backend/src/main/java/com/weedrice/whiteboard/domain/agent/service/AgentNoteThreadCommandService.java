package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.entity.AgentNoteThread;
import com.weedrice.whiteboard.domain.agent.repository.AgentNoteThreadRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class AgentNoteThreadCommandService {

    private final AgentNoteThreadRepository agentNoteThreadRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public AgentNoteThread getOrCreateThread(Agent firstAgent, Agent secondAgent) {
        Long lowAgentId = Math.min(firstAgent.getAgentId(), secondAgent.getAgentId());
        Long highAgentId = Math.max(firstAgent.getAgentId(), secondAgent.getAgentId());
        return agentNoteThreadRepository.findByAgentPairForUpdate(lowAgentId, highAgentId)
                .orElseGet(() -> {
                    agentNoteThreadRepository.insertIgnorePair(lowAgentId, highAgentId);
                    return agentNoteThreadRepository.findByAgentPairForUpdate(lowAgentId, highAgentId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
                });
    }
}
