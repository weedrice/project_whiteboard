package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class AgentWriteAuditRecorder {

    private final AgentAuditService agentAuditService;

    void recordPostCreated(Agent agent, Long postId, AgentRequestContext requestContext) {
        agentAuditService.saveLog(
                agent,
                agent.getUser(),
                AgentAuditActionType.CREATE_POST,
                AgentAuditTargetType.POST,
                postId,
                requestContext);
    }

    void recordCommentCreated(Agent agent, Long commentId, AgentRequestContext requestContext) {
        agentAuditService.saveLog(
                agent,
                agent.getUser(),
                AgentAuditActionType.CREATE_COMMENT,
                AgentAuditTargetType.COMMENT,
                commentId,
                requestContext);
    }
}
