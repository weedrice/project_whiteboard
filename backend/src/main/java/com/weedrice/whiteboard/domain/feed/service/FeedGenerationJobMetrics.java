package com.weedrice.whiteboard.domain.feed.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
class FeedGenerationJobMetrics {
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong processing = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong oldestPendingAgeSeconds = new AtomicLong();
    private final Counter success;
    private final Counter retry;
    private final Counter deadLetter;
    private final Counter recovered;
    private final Counter redrive;

    FeedGenerationJobMetrics(MeterRegistry registry) {
        Gauge.builder("noviis.feed.generation.jobs.pending", pending, AtomicLong::get).register(registry);
        Gauge.builder("noviis.feed.generation.jobs.processing", processing, AtomicLong::get).register(registry);
        Gauge.builder("noviis.feed.generation.jobs.failed", failed, AtomicLong::get).register(registry);
        Gauge.builder("noviis.feed.generation.jobs.oldest_pending_age_seconds",
                oldestPendingAgeSeconds, AtomicLong::get).register(registry);
        success = counter(registry, "success");
        retry = counter(registry, "retry");
        deadLetter = counter(registry, "dead_letter");
        recovered = counter(registry, "recovered");
        redrive = counter(registry, "redrive");
    }

    void update(long pendingCount, long processingCount, long failedCount, long oldestPendingAge) {
        pending.set(pendingCount);
        processing.set(processingCount);
        failed.set(failedCount);
        oldestPendingAgeSeconds.set(Math.max(0L, oldestPendingAge));
    }

    void recordSuccess() { success.increment(); }
    void recordRetry() { retry.increment(); }
    void recordDeadLetter() { deadLetter.increment(); }
    void recordRecovered(int count) { recovered.increment(count); }
    void recordRedrive() { redrive.increment(); }

    private Counter counter(MeterRegistry registry, String result) {
        return Counter.builder("noviis.feed.generation.jobs.processed")
                .tag("result", result)
                .register(registry);
    }
}
