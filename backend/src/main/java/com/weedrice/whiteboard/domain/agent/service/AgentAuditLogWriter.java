package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.entity.AgentActivityLog;
import com.weedrice.whiteboard.domain.agent.repository.AgentActivityLogRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentAuditLogWriter {

    private final AgentActivityLogRepository agentActivityLogRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLog(Long agentId, Long userId, String actionType, String targetType, Long targetId,
            String requestIp, String requestPath) {
        Agent agent = agentId != null ? entityManager.getReference(Agent.class, agentId) : null;
        User user = userId != null ? entityManager.getReference(User.class, userId) : null;
        agentActivityLogRepository.saveAndFlush(AgentActivityLog.builder()
                .agent(agent)
                .user(user)
                .actionType(actionType)
                .targetType(targetType)
                .targetId(targetId)
                .requestIp(requestIp)
                .requestPath(requestPath)
                .build());
    }
}
