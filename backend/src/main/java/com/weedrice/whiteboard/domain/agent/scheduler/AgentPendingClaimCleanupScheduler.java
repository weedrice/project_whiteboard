package com.weedrice.whiteboard.domain.agent.scheduler;

import com.weedrice.whiteboard.domain.agent.config.AgentCleanupProperties;
import com.weedrice.whiteboard.domain.agent.service.AgentLifecycleService;
import com.weedrice.whiteboard.domain.agent.service.AgentPendingClaimPurgeService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class AgentPendingClaimCleanupScheduler {

    private final AgentLifecycleService agentLifecycleService;
    private final AgentPendingClaimPurgeService purgeService;
    private final AgentCleanupProperties properties;
    private final Counter purgedCounter;
    private final Counter failureCounter;
    private final AtomicLong eligibleGauge = new AtomicLong();

    public AgentPendingClaimCleanupScheduler(
            AgentLifecycleService agentLifecycleService,
            AgentPendingClaimPurgeService purgeService,
            AgentCleanupProperties properties,
            MeterRegistry meterRegistry) {
        this.agentLifecycleService = agentLifecycleService;
        this.purgeService = purgeService;
        this.properties = properties;
        this.purgedCounter = meterRegistry.counter("noviis.agent.pending.claim.purged");
        this.failureCounter = meterRegistry.counter("noviis.agent.pending.claim.purge.failures");
        Gauge.builder("noviis.agent.pending.claim.purge.eligible", eligibleGauge, AtomicLong::get)
                .register(meterRegistry);
    }

    @Scheduled(cron = "0 0 * * * ?")
    public void cleanupExpiredPendingClaims() {
        int deletedCount = agentLifecycleService.softDeleteExpiredPendingClaims();
        if (deletedCount > 0) {
            log.info("Soft-deleted {} expired pending agent claim(s)", deletedCount);
        }

        eligibleGauge.set(purgeService.countEligible());
        int totalPurged = 0;
        for (int batch = 0; batch < properties.getPendingClaimPurgeMaxBatches(); batch++) {
            try {
                int purged = purgeService.purgeNextBatch();
                totalPurged += purged;
                if (purged < properties.getPendingClaimPurgeBatchSize()) {
                    break;
                }
            } catch (RuntimeException exception) {
                failureCounter.increment();
                log.error("Failed to hard-delete expired pending agent claims", exception);
                break;
            }
        }
        if (totalPurged > 0) {
            purgedCounter.increment(totalPurged);
            log.info("Hard-deleted {} expired pending agent claim(s)", totalPurged);
        }
        eligibleGauge.set(purgeService.countEligible());
    }
}
