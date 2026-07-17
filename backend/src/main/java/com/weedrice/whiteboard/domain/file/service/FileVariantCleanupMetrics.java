package com.weedrice.whiteboard.domain.file.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
class FileVariantCleanupMetrics {
    private final AtomicLong deadLetter = new AtomicLong();
    private final AtomicLong oldestDueAgeSeconds = new AtomicLong();
    private final Counter retryCounter;
    FileVariantCleanupMetrics(MeterRegistry registry) {
        Gauge.builder("noviis.file.variant.cleanup.dead_letter", deadLetter, AtomicLong::get).register(registry);
        Gauge.builder("noviis.file.variant.cleanup.oldest_due_age_seconds", oldestDueAgeSeconds, AtomicLong::get)
                .register(registry);
        retryCounter = Counter.builder("noviis.file.variant.cleanup")
                .tag("outcome", "retry")
                .register(registry);
    }
    void update(long deadLetterCount, long oldestAge) {
        deadLetter.set(deadLetterCount); oldestDueAgeSeconds.set(Math.max(0, oldestAge));
    }
    void recordRetry() { retryCounter.increment(); }
}
