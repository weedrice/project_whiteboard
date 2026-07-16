package com.weedrice.whiteboard.domain.agent.scheduler;

import com.weedrice.whiteboard.domain.agent.config.AgentCleanupProperties;
import com.weedrice.whiteboard.domain.agent.service.AgentLifecycleService;
import com.weedrice.whiteboard.domain.agent.service.AgentPendingClaimPurgeService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AgentPendingClaimCleanupSchedulerTest {

    @Mock
    private AgentLifecycleService agentLifecycleService;

    @Mock
    private AgentPendingClaimPurgeService purgeService;

    private AgentPendingClaimCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AgentPendingClaimCleanupScheduler(
                agentLifecycleService,
                purgeService,
                new AgentCleanupProperties(),
                new SimpleMeterRegistry());
    }

    @Test
    void cleanupExpiredPendingClaims_delegatesToLifecycleService() {
        scheduler.cleanupExpiredPendingClaims();

        verify(agentLifecycleService).softDeleteExpiredPendingClaims();
    }
}
