package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.entity.AgentActivityLog;
import com.weedrice.whiteboard.domain.agent.repository.AgentActivityLogRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAuditService {

    private final AgentActivityLogRepository agentActivityLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLog(Agent agent, User user, String actionType, String targetType, Long targetId,
            AgentRequestContext context) {
        AgentRequestContext safeContext = context != null ? context : AgentRequestContext.empty();
        try {
            agentActivityLogRepository.save(AgentActivityLog.builder()
                    .agent(agent)
                    .user(user)
                    .actionType(actionType)
                    .targetType(targetType)
                    .targetId(targetId)
                    .requestIp(safeContext.ip())
                    .requestPath(safeContext.path())
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Failed to save agent audit log. agentId={} actionType={} targetType={} targetId={}",
                    agent != null ? agent.getAgentId() : null, actionType, targetType, targetId, ex);
        }
    }
}
