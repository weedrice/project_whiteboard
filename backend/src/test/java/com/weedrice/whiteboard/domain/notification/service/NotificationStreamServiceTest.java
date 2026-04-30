package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    @DisplayName("removeEmitter removes user lock after the last connection")
    void removeEmitter_removesUserLockAfterLastConnection() {
        NotificationStreamService service = new NotificationStreamService(10_000L, 5);

        service.subscribe(1L);
        String connectionId = userEmitters(service, 1L).keySet().iterator().next().toString();

        ReflectionTestUtils.invokeMethod(service, "removeEmitter", 1L, connectionId);

        assertThat(userEmitters(service, 1L)).isNull();
        assertThat(userLocks(service)).doesNotContainKey(1L);
    }

    @Test
    @DisplayName("subscribe retries when the acquired user lock was removed")
    void subscribe_retriesWhenAcquiredUserLockWasRemoved() throws InterruptedException {
        NotificationStreamService service = new NotificationStreamService(10_000L, 5);
        Object staleLock = new Object();
        userLocks(service).put(1L, staleLock);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread subscriber = new Thread(() -> {
            try {
                service.subscribe(1L);
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        subscriber.setDaemon(true);

        synchronized (staleLock) {
            subscriber.start();
            waitUntilBlocked(subscriber);
            userLocks(service).remove(1L, staleLock);
        }
        subscriber.join(1_000L);

        assertThat(subscriber.isAlive()).isFalse();
        assertThat(failure.get()).isNull();
        assertThat(connectionCount(service, 1L)).isEqualTo(1);
        assertThat(userLocks(service)).containsKey(1L);
        assertThat(userLocks(service).get(1L)).isNotSameAs(staleLock);
    }

    @Test
    @DisplayName("heartbeat cleanup does not recreate a missing user lock")
    void heartbeatCleanup_doesNotRecreateMissingUserLock() {
        NotificationStreamService service = new NotificationStreamService(10_000L, 5);
        emitters(service).put(1L, new ConcurrentHashMap<>());

        service.sendHeartbeat();

        assertThat(emitters(service)).doesNotContainKey(1L);
        assertThat(userLocks(service)).doesNotContainKey(1L);
    }

    @Test
    @DisplayName("missing lock cleanup with null expected emitters keeps current emitters")
    void missingLockCleanupWithNullExpectedEmitters_keepsCurrentEmitters() {
        NotificationStreamService service = new NotificationStreamService(10_000L, 5);
        Map<String, Object> currentEmitters = new ConcurrentHashMap<>();
        currentEmitters.put("active", new Object());
        emitters(service).put(1L, currentEmitters);

        ReflectionTestUtils.invokeMethod(service, "removeEmptyUser", 1L, null);

        assertThat(emitters(service).get(1L)).isSameAs(currentEmitters);
        assertThat(userLocks(service)).doesNotContainKey(1L);
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
        return (Map<?, ?>) emitters(service).get(userId);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Map<String, ?>> emitters(NotificationStreamService service) {
        Map<Long, Map<String, ?>> emitters = (Map<Long, Map<String, ?>>) ReflectionTestUtils.getField(service, "emitters");
        assertThat(emitters).isNotNull();
        return emitters;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Object> userLocks(NotificationStreamService service) {
        Map<Long, Object> userLocks = (Map<Long, Object>) ReflectionTestUtils.getField(service, "userLocks");
        assertThat(userLocks).isNotNull();
        return userLocks;
    }

    private void waitUntilBlocked(Thread thread) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (thread.getState() == Thread.State.BLOCKED) {
                return;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("subscriber did not wait for the stale user lock");
    }
}
