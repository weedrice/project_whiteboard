package com.weedrice.whiteboard.domain.search.semantic;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
class SemanticSearchReindexMetrics {
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong processing = new AtomicLong();
    private final AtomicLong oldestActiveAgeSeconds = new AtomicLong();
    SemanticSearchReindexMetrics(MeterRegistry registry) {
        Gauge.builder("noviis.semantic.reindex.jobs.pending", pending, AtomicLong::get).register(registry);
        Gauge.builder("noviis.semantic.reindex.jobs.processing", processing, AtomicLong::get).register(registry);
        Gauge.builder("noviis.semantic.reindex.jobs.oldest_active_age_seconds",
                oldestActiveAgeSeconds, AtomicLong::get).register(registry);
    }
    void update(long pendingCount, long processingCount, long oldestAge) {
        pending.set(pendingCount); processing.set(processingCount);
        oldestActiveAgeSeconds.set(Math.max(0, oldestAge));
    }
}
