package com.weedrice.whiteboard.domain.agent.integration;

import com.weedrice.whiteboard.domain.agent.port.AgentUserRelationshipPort;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentUserRelationshipIntegrationAdapter implements AgentUserRelationshipPort {

    private final UserBlockService userBlockService;

    @Override
    public boolean isBlockedEitherDirection(Long firstUserId, Long secondUserId) {
        return userBlockService.isEitherDirectionBlocked(firstUserId, secondUserId);
    }
}
