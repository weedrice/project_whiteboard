package com.weedrice.whiteboard.domain.user.integration;

import com.weedrice.whiteboard.domain.agent.service.AgentLifecycleService;
import com.weedrice.whiteboard.domain.user.port.UserAgentLifecyclePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserAgentLifecycleIntegrationAdapter implements UserAgentLifecyclePort {

    private final AgentLifecycleService agentLifecycleService;

    @Override
    public void suspendAllForUser(Long userId) {
        agentLifecycleService.suspendAllForUserId(userId);
    }
}
