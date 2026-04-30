package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationStreamServiceTest {

    @Test
    @DisplayName("subscribe uses configured timeout and stores connection")
    void subscribe_usesConfiguredTimeout() {
        NotificationStreamService service = new NotificationStreamService(10_000L, 5);

        SseEmitter emitter = service.subscribe(1L);

        assertThat(ReflectionTestUtils.getField(emitter, "timeout")).isEqualTo(10_000L);
        assertThat(connectionCount(service, 1L)).isEqualTo(1);
    }

    @Test
    @DisplayName("subscribe evicts oldest connection when per-user limit is exceeded")
    void subscribe_evictsOldestConnectionWhenLimitExceeded() {
        NotificationStreamService service = new NotificationStreamService(10_000L, 2);

        SseEmitter first = service.subscribe(1L);
        SseEmitter second = service.subscribe(1L);
        SseEmitter third = service.subscribe(1L);

        assertThat(connectionCount(service, 1L)).isEqualTo(2);
        assertThat(activeEmitters(service, 1L))
                .doesNotContain(first)
                .contains(second, third);
    }

    @Test
    @DisplayName("heartbeat and notification delivery keep healthy connections")
    void heartbeatAndDelivery_keepHealthyConnections() {
        NotificationStreamService service = new NotificationStreamService(10_000L, 5);
        service.subscribe(1L);

        service.sendHeartbeat();
        service.deliverNotification(1L, notification(1L));

        assertThat(connectionCount(service, 1L)).isEqualTo(1);
    }

    private Notification notification(Long userId) {
        User user = User.builder()
                .loginId("receiver")
                .email("receiver@test.com")
                .password("pw")
                .displayName("receiver")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        return Notification.builder()
                .user(user)
                .notificationType(NotificationType.LIKE)
                .sourceType("POST")
                .sourceId(1L)
                .content("notification")
                .build();
    }

    private int connectionCount(NotificationStreamService service, Long userId) {
        Map<?, ?> userEmitters = userEmitters(service, userId);
        return userEmitters == null ? 0 : userEmitters.size();
    }

    private Object[] activeEmitters(NotificationStreamService service, Long userId) {
        return userEmitters(service, userId).values().stream()
                .map(connection -> ReflectionTestUtils.getField(connection, "emitter"))
                .toArray();
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> userEmitters(NotificationStreamService service, Long userId) {
        Map<Long, ?> emitters = (Map<Long, ?>) ReflectionTestUtils.getField(service, "emitters");
        assertThat(emitters).isNotNull();
        return (Map<?, ?>) emitters.get(userId);
    }
}
