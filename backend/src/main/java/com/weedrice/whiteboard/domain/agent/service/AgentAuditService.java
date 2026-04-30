package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAuditService {

    private final AgentAuditLogWriter agentAuditLogWriter;

    public void saveLog(Agent agent, User user, String actionType, String targetType, Long targetId,
            AgentRequestContext context) {
        AgentRequestContext safeContext = context != null ? context : AgentRequestContext.empty();
        AgentAuditCommand command = new AgentAuditCommand(
                agent != null ? agent.getAgentId() : null,
                user != null ? user.getUserId() : null,
                actionType,
                targetType,
                targetId,
                safeContext.ip(),
                safeContext.path());

        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            writeLog(command);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                writeLog(command);
            }
        });
    }

    private void writeLog(AgentAuditCommand command) {
        try {
            agentAuditLogWriter.saveLog(
                    command.agentId(),
                    command.userId(),
                    command.actionType(),
                    command.targetType(),
                    command.targetId(),
                    command.requestIp(),
                    command.requestPath());
        } catch (RuntimeException ex) {
            log.warn("Failed to dispatch agent audit log. agentId={} actionType={} targetType={} targetId={}",
                    command.agentId(), command.actionType(), command.targetType(), command.targetId(), ex);
        }
    }

    private record AgentAuditCommand(
            Long agentId,
            Long userId,
            String actionType,
            String targetType,
            Long targetId,
            String requestIp,
            String requestPath) {
    }
}
