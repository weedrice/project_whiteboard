package com.weedrice.whiteboard.domain.notification.web;

import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.weedrice.whiteboard.global.exception.BusinessException;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationSseEmitterRegistryTest {

    @Test
    void commentTopicRequiresActiveEmitter() {
        NotificationSseEmitterRegistry registry = new NotificationSseEmitterRegistry(10_000L, 5);

        assertThatThrownBy(() -> registry.subscribeCommentTopic(1L, 10L, "subscriber"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void removingLastEmitterClearsCommentTopics() {
        NotificationSseEmitterRegistry registry = new NotificationSseEmitterRegistry(10_000L, 5);
        registry.subscribe(1L);
        registry.subscribeCommentTopic(1L, 10L, "subscriber");
        String connectionId = emitters(registry).get(1L).keySet().iterator().next();

        ReflectionTestUtils.invokeMethod(registry, "removeEmitter", 1L, connectionId);

        Map<?, ?> commentSubscribers = (Map<?, ?>) ReflectionTestUtils.getField(registry, "commentSubscribers");
        assertThat(commentSubscribers).isEmpty();
    }

    @Test
    void rejectsCommentTopicBeyondPerUserLimit() {
        NotificationSseEmitterRegistry registry = new NotificationSseEmitterRegistry(10_000L, 5);
        registry.subscribe(1L);
        for (long postId = 1L; postId <= 100L; postId++) {
            registry.subscribeCommentTopic(1L, postId, "subscriber");
        }

        assertThatThrownBy(() -> registry.subscribeCommentTopic(1L, 101L, "subscriber"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsSubscriberBeyondPerTopicLimit() {
        NotificationSseEmitterRegistry registry = new NotificationSseEmitterRegistry(10_000L, 5);
        registry.subscribe(1L);
        for (int subscriber = 1; subscriber <= 10; subscriber++) {
            registry.subscribeCommentTopic(1L, 10L, "subscriber-" + subscriber);
        }

        assertThatThrownBy(() -> registry.subscribeCommentTopic(1L, 10L, "subscriber-11"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("subscribe uses configured timeout and stores connection")
    void subscribe_usesConfiguredTimeout() {
        NotificationSseEmitterRegistry registry = new NotificationSseEmitterRegistry(10_000L, 5);

        SseEmitter emitter = registry.subscribe(1L);

        assertThat(ReflectionTestUtils.getField(emitter, "timeout")).isEqualTo(10_000L);
        assertThat(connectionCount(registry, 1L)).isEqualTo(1);
    }

    @Test
    @DisplayName("subscribe evicts oldest connection when per-user limit is exceeded")
    void subscribe_evictsOldestConnectionWhenLimitExceeded() {
        NotificationSseEmitterRegistry registry = new NotificationSseEmitterRegistry(10_000L, 2);

        SseEmitter first = registry.subscribe(1L);
        SseEmitter second = registry.subscribe(1L);
        SseEmitter third = registry.subscribe(1L);

        assertThat(connectionCount(registry, 1L)).isEqualTo(2);
        assertThat(activeEmitters(registry, 1L))
                .doesNotContain(first)
                .contains(second, third);
    }

    @Test
    @DisplayName("heartbeat and notification delivery keep healthy connections")
    void heartbeatAndDelivery_keepHealthyConnections() {
        NotificationSseEmitterRegistry registry = new NotificationSseEmitterRegistry(10_000L, 5);
        registry.subscribe(1L);

        registry.sendHeartbeat();
        registry.publish(1L, summary(1L));

        assertThat(connectionCount(registry, 1L)).isEqualTo(1);
    }

    @Test
    @DisplayName("subscribe removes and completes emitter with error when connect event fails")
    void subscribe_connectEventFails_completesEmitterWithError() {
        FailingSseEmitter emitter = FailingSseEmitter.failImmediately();
        NotificationSseEmitterRegistry registry = new FixedEmitterNotificationSseEmitterRegistry(emitter);

        SseEmitter subscribed = registry.subscribe(1L);

        assertThat(subscribed).isSameAs(emitter);
        assertThat(connectionCount(registry, 1L)).isZero();
        assertThat(emitter.completedWithError()).isTrue();
        assertThat(emitter.completionError()).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("heartbeat failure removes and completes emitter with error")
    void sendHeartbeat_sendFails_completesEmitterWithError() {
        FailingSseEmitter emitter = FailingSseEmitter.failAfterConnectEvent();
        NotificationSseEmitterRegistry registry = new FixedEmitterNotificationSseEmitterRegistry(emitter);
        registry.subscribe(1L);

        registry.sendHeartbeat();

        assertThat(connectionCount(registry, 1L)).isZero();
        assertThat(emitter.completedWithError()).isTrue();
        assertThat(emitter.completionError()).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("notification delivery failure removes and completes emitter with error")
    void deliverNotification_sendFails_completesEmitterWithError() {
        FailingSseEmitter emitter = FailingSseEmitter.failAfterConnectEvent();
        NotificationSseEmitterRegistry registry = new FixedEmitterNotificationSseEmitterRegistry(emitter);
        registry.subscribe(1L);

        registry.publish(1L, summary(1L));

        assertThat(connectionCount(registry, 1L)).isZero();
        assertThat(emitter.completedWithError()).isTrue();
        assertThat(emitter.completionError()).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("removeEmitter removes user lock after the last connection")
    void removeEmitter_removesUserLockAfterLastConnection() {
        NotificationSseEmitterRegistry registry = new NotificationSseEmitterRegistry(10_000L, 5);

        registry.subscribe(1L);
        String connectionId = userEmitters(registry, 1L).keySet().iterator().next().toString();

        ReflectionTestUtils.invokeMethod(registry, "removeEmitter", 1L, connectionId);

        assertThat(userEmitters(registry, 1L)).isNull();
        assertThat(userLocks(registry)).doesNotContainKey(1L);
    }

    @Test
    @DisplayName("subscribe retries when the acquired user lock was removed")
    void subscribe_retriesWhenAcquiredUserLockWasRemoved() throws InterruptedException {
        NotificationSseEmitterRegistry registry = new NotificationSseEmitterRegistry(10_000L, 5);
        Object staleLock = new Object();
        userLocks(registry).put(1L, staleLock);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread subscriber = new Thread(() -> {
            try {
                registry.subscribe(1L);
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        subscriber.setDaemon(true);

        synchronized (staleLock) {
            subscriber.start();
            waitUntilBlocked(subscriber);
            userLocks(registry).remove(1L, staleLock);
        }
        subscriber.join(1_000L);

        assertThat(subscriber.isAlive()).isFalse();
        assertThat(failure.get()).isNull();
        assertThat(connectionCount(registry, 1L)).isEqualTo(1);
        assertThat(userLocks(registry)).containsKey(1L);
        assertThat(userLocks(registry).get(1L)).isNotSameAs(staleLock);
    }

    @Test
    @DisplayName("heartbeat cleanup does not recreate a missing user lock")
    void heartbeatCleanup_doesNotRecreateMissingUserLock() {
        NotificationSseEmitterRegistry registry = new NotificationSseEmitterRegistry(10_000L, 5);
        emitters(registry).put(1L, new ConcurrentHashMap<>());

        registry.sendHeartbeat();

        assertThat(emitters(registry)).doesNotContainKey(1L);
        assertThat(userLocks(registry)).doesNotContainKey(1L);
    }

    @Test
    @DisplayName("missing lock cleanup with null expected emitters keeps current emitters")
    void missingLockCleanupWithNullExpectedEmitters_keepsCurrentEmitters() {
        NotificationSseEmitterRegistry registry = new NotificationSseEmitterRegistry(10_000L, 5);
        Map<String, Object> currentEmitters = new ConcurrentHashMap<>();
        currentEmitters.put("active", new Object());
        emitters(registry).put(1L, currentEmitters);

        ReflectionTestUtils.invokeMethod(registry, "removeEmptyUser", 1L, null);

        assertThat(emitters(registry).get(1L)).isSameAs(currentEmitters);
        assertThat(userLocks(registry)).doesNotContainKey(1L);
    }

    private NotificationResponse.NotificationSummary summary(Long userId) {
        User user = User.builder()
                .loginId("receiver")
                .email("receiver@test.com")
                .password("pw")
                .displayName("receiver")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        Notification notification = Notification.builder()
                .user(user)
                .notificationType(NotificationType.LIKE)
                .sourceType("POST")
                .sourceId(1L)
                .content("notification")
                .build();
        return NotificationResponse.NotificationSummary.from(notification);
    }

    private int connectionCount(NotificationSseEmitterRegistry registry, Long userId) {
        Map<?, ?> userEmitters = userEmitters(registry, userId);
        return userEmitters == null ? 0 : userEmitters.size();
    }

    private Object[] activeEmitters(NotificationSseEmitterRegistry registry, Long userId) {
        return userEmitters(registry, userId).values().stream()
                .map(connection -> ReflectionTestUtils.getField(connection, "emitter"))
                .toArray();
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> userEmitters(NotificationSseEmitterRegistry registry, Long userId) {
        return (Map<?, ?>) emitters(registry).get(userId);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Map<String, ?>> emitters(NotificationSseEmitterRegistry registry) {
        Map<Long, Map<String, ?>> emitters =
                (Map<Long, Map<String, ?>>) ReflectionTestUtils.getField(registry, "emitters");
        assertThat(emitters).isNotNull();
        return emitters;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Object> userLocks(NotificationSseEmitterRegistry registry) {
        Map<Long, Object> userLocks = (Map<Long, Object>) ReflectionTestUtils.getField(registry, "userLocks");
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

    private static class FixedEmitterNotificationSseEmitterRegistry extends NotificationSseEmitterRegistry {

        private final SseEmitter emitter;

        private FixedEmitterNotificationSseEmitterRegistry(SseEmitter emitter) {
            super(10_000L, 5);
            this.emitter = emitter;
        }

        @Override
        SseEmitter createEmitter() {
            return emitter;
        }
    }

    private static class FailingSseEmitter extends SseEmitter {

        private final int successfulSends;
        private int sends;
        private boolean completedWithError;
        private Throwable completionError;

        private FailingSseEmitter(int successfulSends) {
            super(10_000L);
            this.successfulSends = successfulSends;
        }

        private static FailingSseEmitter failImmediately() {
            return new FailingSseEmitter(0);
        }

        private static FailingSseEmitter failAfterConnectEvent() {
            return new FailingSseEmitter(1);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (sends++ >= successfulSends) {
                throw new IOException("send failed");
            }
        }

        @Override
        public void completeWithError(Throwable ex) {
            this.completedWithError = true;
            this.completionError = ex;
        }

        private boolean completedWithError() {
            return completedWithError;
        }

        private Throwable completionError() {
            return completionError;
        }
    }
}
