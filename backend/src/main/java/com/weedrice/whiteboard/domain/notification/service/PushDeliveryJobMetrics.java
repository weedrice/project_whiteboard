package com.weedrice.whiteboard.domain.notification.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
class PushDeliveryJobMetrics {

    private final MeterRegistry meterRegistry;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong oldestPendingAgeSeconds = new AtomicLong();

    PushDeliveryJobMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Gauge.builder("noviis.webpush.job.backlog", pending, AtomicLong::get)
                .tag("status", "pending")
                .register(meterRegistry);
        Gauge.builder("noviis.webpush.job.backlog", failed, AtomicLong::get)
                .tag("status", "failed")
                .register(meterRegistry);
        Gauge.builder("noviis.webpush.job.oldest.age.seconds", oldestPendingAgeSeconds, AtomicLong::get)
                .register(meterRegistry);
    }

    void recordOutcome(String outcome) {
        meterRegistry.counter("noviis.webpush.job.outcome", "outcome", outcome).increment();
    }

    void updateBacklog(long pendingCount, long failedCount, long oldestAgeSeconds) {
        pending.set(Math.max(0, pendingCount));
        failed.set(Math.max(0, failedCount));
        oldestPendingAgeSeconds.set(Math.max(0, oldestAgeSeconds));
    }
}
