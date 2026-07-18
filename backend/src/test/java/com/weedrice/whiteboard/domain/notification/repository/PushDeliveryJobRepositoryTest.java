package com.weedrice.whiteboard.domain.notification.repository;

import com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class PushDeliveryJobRepositoryTest {

    @Autowired
    private PushDeliveryJobRepository repository;

    @Test
    void h2InsertFallbackKeepsEventAndSubscriptionIdempotent() {
        UUID eventId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 12, 0);

        boolean first = repository.insertIfAbsent(pending(eventId, now));
        boolean duplicate = repository.insertIfAbsent(pending(eventId, now));

        assertThat(first).isTrue();
        assertThat(duplicate).isFalse();
        assertThat(repository.count()).isOne();
    }

    @Test
    void receiverRevocationExpiresActiveJobsAndRedactsSnapshots() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 12, 0);
        repository.insertIfAbsent(pending(UUID.randomUUID(), now));

        assertThat(repository.redactForReceiver(2L)).isOne();

        PushDeliveryJob redacted = repository.findAll().getFirst();
        assertThat(redacted.getStatus()).isEqualTo(PushDeliveryJob.Status.EXPIRED);
        assertThat(redacted.getEndpoint()).isNull();
        assertThat(redacted.getP256dh()).isNull();
        assertThat(redacted.getAuth()).isNull();
        assertThat(redacted.getPayload()).isNull();
    }

    private PushDeliveryJob pending(UUID eventId, LocalDateTime now) {
        return PushDeliveryJob.pending(
                eventId, 1L, 2L, "https://push.example/subscription",
                "public-key", "auth-secret", now.minusMinutes(1), "payload", now);
    }
}
