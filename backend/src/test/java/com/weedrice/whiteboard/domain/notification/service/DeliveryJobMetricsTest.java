package com.weedrice.whiteboard.domain.notification.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryJobMetricsTest {

    @Test
    void notificationOutcomesExistBeforeTheFirstJobRuns() {
        var registry = new SimpleMeterRegistry();

        new NotificationDeliveryJobMetrics(registry);

        assertThat(registry.get("noviis.notification.delivery.jobs")
                .tag("outcome", "invalid_payload").counter().count()).isZero();
        assertThat(registry.get("noviis.notification.delivery.jobs")
                .tag("outcome", "lease_lost").counter().count()).isZero();
        assertThat(registry.get("noviis.notification.delivery.jobs")
                .tag("outcome", "transition_invalid").counter().count()).isZero();
    }

    @Test
    void pushOutcomesExistBeforeTheFirstJobRuns() {
        var registry = new SimpleMeterRegistry();

        PushDeliveryJobMetrics metrics = new PushDeliveryJobMetrics(registry);

        assertThat(registry.get("noviis.webpush.job.outcome")
                .tag("outcome", "invalid_payload").counter().count()).isZero();
        assertThat(registry.get("noviis.webpush.job.outcome")
                .tag("outcome", "lease_lost").counter().count()).isZero();
        assertThat(registry.get("noviis.webpush.job.outcome")
                .tag("outcome", "transition_invalid").counter().count()).isZero();
        assertThat(registry.get("noviis.webpush.job.outcome")
                .tag("outcome", "stale_subscription").counter().count()).isZero();
        assertThat(registry.get("noviis.webpush.job.outcome")
                .tag("outcome", "expired").counter().count()).isZero();
        assertThatThrownBy(() -> metrics.recordOutcome("unregistered"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
