package com.weedrice.whiteboard.domain.notification.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PushDeliveryJobMetricsTest {

    @Test
    void actorExclusionOutcomesAreRegisteredAndRecordable() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            PushDeliveryJobMetrics metrics = new PushDeliveryJobMetrics(registry);

            metrics.recordOutcome("blocked_actor");
            metrics.recordOutcome("hidden_actor");
            metrics.recordOutcome("hidden_source");

            assertThat(registry.get("noviis.webpush.job.outcome").tag("outcome", "blocked_actor")
                    .counter().count()).isEqualTo(1);
            assertThat(registry.get("noviis.webpush.job.outcome").tag("outcome", "hidden_actor")
                    .counter().count()).isEqualTo(1);
            assertThat(registry.get("noviis.webpush.job.outcome").tag("outcome", "hidden_source")
                    .counter().count()).isEqualTo(1);
        } finally {
            registry.close();
        }
    }
}
