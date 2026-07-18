package com.weedrice.whiteboard.domain.notification.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.List;

@Component
class NotificationDeliveryJobMetrics {

    private static final List<String> OUTCOMES = List.of(
            "success",
            "invalid_payload",
            "retry",
            "dead_letter",
            "lease_recovered",
            "lease_lost",
            "transition_invalid",
            "cleanup_failure",
            "redrive");

    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong deadLetter = new AtomicLong();
    private final AtomicLong oldestDueAgeSeconds = new AtomicLong();
    private final AtomicLong oldestLeaseAgeSeconds = new AtomicLong();
    private final MeterRegistry meterRegistry;

    NotificationDeliveryJobMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Gauge.builder("noviis.notification.delivery.jobs.pending", pending, AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder("noviis.notification.delivery.jobs.dead_letter", deadLetter, AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder("noviis.notification.delivery.jobs.oldest_due_age_seconds", oldestDueAgeSeconds, AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder("noviis.notification.delivery.jobs.oldest_lease_age_seconds", oldestLeaseAgeSeconds, AtomicLong::get)
                .register(meterRegistry);
        OUTCOMES.forEach(outcome -> meterRegistry.counter(
                "noviis.notification.delivery.jobs", "outcome", outcome));
    }

    void recordOutcome(String outcome) {
        meterRegistry.counter("noviis.notification.delivery.jobs", "outcome", outcome).increment();
    }

    void updateBacklog(long pendingCount, long deadLetterCount) {
        pending.set(pendingCount);
        deadLetter.set(deadLetterCount);
    }

    void updateAges(long dueAgeSeconds, long leaseAgeSeconds) {
        oldestDueAgeSeconds.set(Math.max(0, dueAgeSeconds));
        oldestLeaseAgeSeconds.set(Math.max(0, leaseAgeSeconds));
    }
}
