package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.entity.AgentActivityLog;
import com.weedrice.whiteboard.domain.agent.repository.AgentActivityLogRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAuditLogWriter {
    private static final int MAX_REQUEST_IP_LENGTH = 45;
    private static final int MAX_REQUEST_PATH_LENGTH = 255;

    private final AgentActivityLogRepository agentActivityLogRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLog(Long agentId, Long userId, String actionType, String targetType, Long targetId,
            String requestIp, String requestPath) {
        Agent agent = agentId != null ? entityManager.getReference(Agent.class, agentId) : null;
        User user = userId != null ? entityManager.getReference(User.class, userId) : null;
        String normalizedRequestIp = normalizeRequestMetadata(requestIp, MAX_REQUEST_IP_LENGTH, "requestIp");
        String normalizedRequestPath = normalizeRequestMetadata(requestPath, MAX_REQUEST_PATH_LENGTH, "requestPath");
        agentActivityLogRepository.saveAndFlush(AgentActivityLog.builder()
                .agent(agent)
                .user(user)
                .actionType(actionType)
                .targetType(targetType)
                .targetId(targetId)
                .requestIp(normalizedRequestIp)
                .requestPath(normalizedRequestPath)
                .build());
    }

    private String normalizeRequestMetadata(String value, int maxLength, String fieldName) {
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        if (trimmedValue.length() <= maxLength) {
            return trimmedValue;
        }
        log.warn("Agent audit log metadata truncated. field={} originalLength={} maxLength={}",
                fieldName, trimmedValue.length(), maxLength);
        return trimmedValue.substring(0, maxLength);
    }
}
