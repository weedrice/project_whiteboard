package com.weedrice.whiteboard.domain.search.semantic;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
class SemanticSearchReindexMetrics {
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong processing = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong oldestActiveAgeSeconds = new AtomicLong();
    private final Counter success;
    private final Counter retry;
    private final Counter deadLetter;
    private final Counter recovered;
    private final Counter redrive;
    private final Counter claimError;
    private final Counter failureStateError;

    SemanticSearchReindexMetrics(MeterRegistry registry) {
        Gauge.builder("noviis.semantic.reindex.jobs.pending", pending, AtomicLong::get).register(registry);
        Gauge.builder("noviis.semantic.reindex.jobs.processing", processing, AtomicLong::get).register(registry);
        Gauge.builder("noviis.semantic.reindex.jobs.failed", failed, AtomicLong::get).register(registry);
        Gauge.builder("noviis.semantic.reindex.jobs.oldest_active_age_seconds",
                oldestActiveAgeSeconds, AtomicLong::get).register(registry);
        success = resultCounter(registry, "success");
        retry = resultCounter(registry, "retry");
        deadLetter = resultCounter(registry, "dead_letter");
        recovered = resultCounter(registry, "recovered");
        redrive = resultCounter(registry, "redrive");
        claimError = resultCounter(registry, "claim_error");
        failureStateError = resultCounter(registry, "failure_state_error");
    }

    void update(long pendingCount, long processingCount, long failedCount, long oldestAge) {
        pending.set(pendingCount);
        processing.set(processingCount);
        failed.set(failedCount);
        oldestActiveAgeSeconds.set(Math.max(0, oldestAge));
    }

    void recordSuccess() { success.increment(); }
    void recordRetry() { retry.increment(); }
    void recordDeadLetter() { deadLetter.increment(); }
    void recordRecovered(int count) { recovered.increment(count); }
    void recordRedrive() { redrive.increment(); }
    void recordClaimError() { claimError.increment(); }
    void recordFailureStateError() { failureStateError.increment(); }

    private Counter resultCounter(MeterRegistry registry, String result) {
        return Counter.builder("noviis.semantic.reindex.jobs.processed")
                .tag("result", result)
                .register(registry);
    }
}
