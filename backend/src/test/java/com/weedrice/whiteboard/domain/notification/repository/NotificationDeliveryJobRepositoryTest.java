package com.weedrice.whiteboard.domain.notification.repository;

import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class NotificationDeliveryJobRepositoryTest {

    @Autowired
    NotificationDeliveryJobRepository repository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void h2FallbackInsertIsIdempotentForSameEvent() {
        User receiver = User.builder()
                .loginId("delivery-receiver")
                .email("delivery-receiver@example.com")
                .password("password")
                .displayName("Delivery Receiver")
                .build();
        entityManager.persistAndFlush(receiver);
        NotificationEvent event = new NotificationEvent(
                receiver,
                null,
                NotificationType.SYSTEM,
                NotificationSourceType.SYSTEM,
                10L,
                "delivery");
        LocalDateTime now = LocalDateTime.of(2026, 7, 17, 0, 0);

        int inserted = repository.insertIfAbsent(NotificationDeliveryJob.from(event, now), now);
        int duplicate = repository.insertIfAbsent(NotificationDeliveryJob.from(event, now), now);

        assertThat(inserted).isEqualTo(1);
        assertThat(duplicate).isZero();
        assertThat(repository.findByEventId(event.getEventId())).isPresent();
        assertThat(repository.count()).isEqualTo(1);
    }
}
