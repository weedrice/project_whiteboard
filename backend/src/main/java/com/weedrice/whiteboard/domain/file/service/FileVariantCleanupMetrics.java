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
    private final AtomicLong reconciliationBackoffFiles = new AtomicLong();
    private final Counter reconciliationSuccessCounter;
    private final Counter reconciliationFailureCounter;
    private final Counter reconciliationDeferredCounter;
    FileVariantCleanupMetrics(MeterRegistry registry) {
        Gauge.builder("noviis.file.variant.cleanup.dead_letter", deadLetter, AtomicLong::get).register(registry);
        Gauge.builder("noviis.file.variant.cleanup.oldest_due_age_seconds", oldestDueAgeSeconds, AtomicLong::get)
                .register(registry);
        Gauge.builder("noviis.file.variant.reconciliation.backoff_files", reconciliationBackoffFiles, AtomicLong::get)
                .register(registry);
        reconciliationSuccessCounter = reconciliationCounter(registry, "success");
        reconciliationFailureCounter = reconciliationCounter(registry, "failure");
        reconciliationDeferredCounter = reconciliationCounter(registry, "deferred");
        retryCounter = Counter.builder("noviis.file.variant.cleanup")
                .tag("outcome", "retry")
                .register(registry);
    }
    private static Counter reconciliationCounter(MeterRegistry registry, String outcome) {
        return Counter.builder("noviis.file.variant.reconciliation").tag("outcome", outcome).register(registry);
    }

    void recordReconciliationSuccess() {
        reconciliationSuccessCounter.increment();
    }

    void recordReconciliationFailure() {
        reconciliationFailureCounter.increment();
    }

    void recordReconciliationDeferred() {
        reconciliationDeferredCounter.increment();
    }

    void updateReconciliationBackoffFiles(long count) {
        reconciliationBackoffFiles.set(count);
    }

    void update(long deadLetterCount, long oldestAge) {
        deadLetter.set(deadLetterCount); oldestDueAgeSeconds.set(Math.max(0, oldestAge));
    }
    void recordRetry() { retryCounter.increment(); }
}
