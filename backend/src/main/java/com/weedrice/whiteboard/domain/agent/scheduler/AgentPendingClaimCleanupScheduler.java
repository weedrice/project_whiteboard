package com.weedrice.whiteboard.domain.agent.scheduler;

import com.weedrice.whiteboard.domain.agent.service.AgentLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentPendingClaimCleanupScheduler {

    private final AgentLifecycleService agentLifecycleService;

    @Scheduled(cron = "0 0 * * * ?")
    public void cleanupExpiredPendingClaims() {
        int deletedCount = agentLifecycleService.softDeleteExpiredPendingClaims();
        if (deletedCount > 0) {
            log.info("Soft-deleted {} expired pending agent claim(s)", deletedCount);
        }
    }
}
