package com.weedrice.whiteboard.domain.feed.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeedGenerationJobMetricsTest {

    @Test
    void exposesQueueGaugesAndOutcomeCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FeedGenerationJobMetrics metrics = new FeedGenerationJobMetrics(registry);

        metrics.update(3, 2, 1, -5);
        metrics.recordSuccess();
        metrics.recordRetry();
        metrics.recordDeadLetter();
        metrics.recordRecovered(2);
        metrics.recordRedrive();

        assertThat(registry.get("noviis.feed.generation.jobs.pending").gauge().value()).isEqualTo(3);
        assertThat(registry.get("noviis.feed.generation.jobs.processing").gauge().value()).isEqualTo(2);
        assertThat(registry.get("noviis.feed.generation.jobs.failed").gauge().value()).isEqualTo(1);
        assertThat(registry.get("noviis.feed.generation.jobs.oldest_pending_age_seconds").gauge().value())
                .isZero();
        assertThat(counter(registry, "success")).isEqualTo(1);
        assertThat(counter(registry, "retry")).isEqualTo(1);
        assertThat(counter(registry, "dead_letter")).isEqualTo(1);
        assertThat(counter(registry, "recovered")).isEqualTo(2);
        assertThat(counter(registry, "redrive")).isEqualTo(1);
    }

    private double counter(SimpleMeterRegistry registry, String result) {
        return registry.get("noviis.feed.generation.jobs.processed")
                .tag("result", result)
                .counter()
                .count();
    }
}
