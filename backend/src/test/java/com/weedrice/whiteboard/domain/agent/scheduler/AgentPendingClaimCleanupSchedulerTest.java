package com.weedrice.whiteboard.domain.agent.scheduler;

import com.weedrice.whiteboard.domain.agent.service.AgentLifecycleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AgentPendingClaimCleanupSchedulerTest {

    @Mock
    private AgentLifecycleService agentLifecycleService;

    @InjectMocks
    private AgentPendingClaimCleanupScheduler scheduler;

    @Test
    void cleanupExpiredPendingClaims_delegatesToLifecycleService() {
        scheduler.cleanupExpiredPendingClaims();

        verify(agentLifecycleService).softDeleteExpiredPendingClaims();
    }
}
