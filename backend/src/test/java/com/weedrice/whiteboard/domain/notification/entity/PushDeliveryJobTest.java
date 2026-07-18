package com.weedrice.whiteboard.domain.notification.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PushDeliveryJobTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 18, 12, 0);

    @Test
    void completionRedactsDeliverySecretsAndPayload() {
        PushDeliveryJob job = pending();
        job.claim(NOW);

        job.complete();

        assertTerminalSnapshotRedacted(job, PushDeliveryJob.Status.COMPLETED);
    }

    @Test
    void deadLetterRedactsDeliverySecretsAndPayload() {
        PushDeliveryJob job = pending();
        job.claim(NOW);

        assertThat(job.retry("failed", NOW, NOW.plusMinutes(1), 1)).isTrue();

        assertTerminalSnapshotRedacted(job, PushDeliveryJob.Status.FAILED);
    }

    @Test
    void retryableFailureKeepsSnapshotForNextAttempt() {
        PushDeliveryJob job = pending();
        job.claim(NOW);

        assertThat(job.retry("failed", NOW, NOW.plusMinutes(1), 5)).isFalse();

        assertThat(job.getStatus()).isEqualTo(PushDeliveryJob.Status.PENDING);
        assertThat(job.getEndpoint()).isEqualTo("https://push.example/subscription");
        assertThat(job.getPayload()).isEqualTo("payload");
    }

    private PushDeliveryJob pending() {
        return PushDeliveryJob.pending(
                UUID.randomUUID(), 1L, 2L, "https://push.example/subscription",
                "public-key", "auth-secret", NOW.minusMinutes(1), "payload", NOW);
    }

    private void assertTerminalSnapshotRedacted(PushDeliveryJob job, PushDeliveryJob.Status status) {
        assertThat(job.getStatus()).isEqualTo(status);
        assertThat(job.getEndpoint()).isNull();
        assertThat(job.getP256dh()).isNull();
        assertThat(job.getAuth()).isNull();
        assertThat(job.getPayload()).isNull();
    }
}
