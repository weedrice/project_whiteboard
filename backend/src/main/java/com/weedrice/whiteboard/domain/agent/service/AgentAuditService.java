package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.entity.AgentActivityLog;
import com.weedrice.whiteboard.domain.agent.repository.AgentActivityLogRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.util.ClientUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentAuditService {

    private final AgentActivityLogRepository agentActivityLogRepository;

    @Transactional
    public void saveLog(Agent agent, User user, String actionType, String targetType, Long targetId,
            HttpServletRequest request) {
        agentActivityLogRepository.save(AgentActivityLog.builder()
                .agent(agent)
                .user(user)
                .actionType(actionType)
                .targetType(targetType)
                .targetId(targetId)
                .requestIp(request != null ? ClientUtils.getIp(request) : null)
                .requestPath(request != null ? request.getRequestURI() : null)
                .build());
    }
}
