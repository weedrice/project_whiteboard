package com.weedrice.whiteboard.domain.notification.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
class KeywordNotificationFanoutMetrics {
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong deadLetter = new AtomicLong();
    private final AtomicLong oldestDueAgeSeconds = new AtomicLong();
    private final Counter retryCounter;

    KeywordNotificationFanoutMetrics(MeterRegistry registry) {
        Gauge.builder("noviis.notification.keyword.fanout.jobs.pending", pending, AtomicLong::get).register(registry);
        Gauge.builder("noviis.notification.keyword.fanout.jobs.dead_letter", deadLetter, AtomicLong::get).register(registry);
        Gauge.builder("noviis.notification.keyword.fanout.jobs.oldest_due_age_seconds",
                oldestDueAgeSeconds, AtomicLong::get).register(registry);
        retryCounter = Counter.builder("noviis.notification.keyword.fanout.jobs")
                .tag("outcome", "retry")
                .register(registry);
    }
    void update(long pendingCount, long failedCount, long oldestAge) {
        pending.set(pendingCount); deadLetter.set(failedCount); oldestDueAgeSeconds.set(Math.max(0, oldestAge));
    }
    void recordRetry() { retryCounter.increment(); }
}
