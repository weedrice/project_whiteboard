package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.entity.AgentNote;
import com.weedrice.whiteboard.domain.agent.entity.AgentNoteThread;
import com.weedrice.whiteboard.domain.agent.repository.AgentNoteRepository;
import com.weedrice.whiteboard.domain.agent.service.AgentPolicyService.AgentPolicySnapshot;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class AgentNoteSendCommandService {

    private final AgentQuotaService agentQuotaService;
    private final AgentNoteThreadCommandService agentNoteThreadCommandService;
    private final AgentNoteRepository agentNoteRepository;
    private final AgentAuditService agentAuditService;
    private final AgentNoteSendPolicy agentNoteSendPolicy;

    @Transactional(propagation = Propagation.MANDATORY)
    public AgentNoteSendResult send(Agent sender, Agent recipient, String content,
            AgentPolicySnapshot policy, AgentRequestContext requestContext) {
        reserveNoteSend(sender, policy);
        AgentNoteThread thread = agentNoteThreadCommandService.getOrCreateThread(sender, recipient);
        AgentNote note = agentNoteRepository.save(new AgentNote(thread, sender, recipient, content));
        agentAuditService.saveLog(
                sender,
                sender.getUser(),
                AgentAuditActionType.SEND_NOTE,
                AgentAuditTargetType.NOTE,
                note.getNoteId(),
                requestContext);
        return new AgentNoteSendResult(thread, note);
    }

    private void reserveNoteSend(Agent agent, AgentPolicySnapshot policy) {
        try {
            agentQuotaService.reserveNoteSend(agent);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.RATE_LIMIT_EXCEEDED) {
                throw agentNoteSendPolicy.noteDailyLimitExceeded(e.getMessage(), policy);
            }
            throw e;
        }
    }

    public record AgentNoteSendResult(AgentNoteThread thread, AgentNote note) {
    }
}
