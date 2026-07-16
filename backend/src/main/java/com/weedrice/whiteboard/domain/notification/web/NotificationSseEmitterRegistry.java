package com.weedrice.whiteboard.domain.notification.web;

import com.weedrice.whiteboard.domain.notification.dto.CommentStreamEvent;
import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.service.CommentStreamPublisher;
import com.weedrice.whiteboard.domain.notification.service.NotificationStreamPublisher;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class NotificationSseEmitterRegistry implements NotificationStreamPublisher, CommentStreamPublisher {

    private static final long DEFAULT_TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();
    private static final int DEFAULT_MAX_CONNECTIONS_PER_USER = 5;

    private final Map<Long, Map<String, EmitterConnection>> emitters = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, ConcurrentMap<Long, ConcurrentMap<String, Boolean>>> commentSubscribers =
            new ConcurrentHashMap<>();
    private final Map<Long, Object> userLocks = new ConcurrentHashMap<>();
    private final AtomicLong connectionSequence = new AtomicLong();
    private final AtomicLong lastHeartbeatMillis = new AtomicLong();
    private final Counter heartbeatFailures;
    private final long timeoutMillis;
    private final int maxConnectionsPerUser;

    @Autowired
    NotificationSseEmitterRegistry(
            @Value("${notification.stream.timeout-millis:1800000}") long timeoutMillis,
            @Value("${notification.stream.max-connections-per-user:5}") int maxConnectionsPerUser,
            MeterRegistry meterRegistry) {
        this.timeoutMillis = timeoutMillis > 0 ? timeoutMillis : DEFAULT_TIMEOUT_MILLIS;
        this.maxConnectionsPerUser = maxConnectionsPerUser > 0
                ? maxConnectionsPerUser
                : DEFAULT_MAX_CONNECTIONS_PER_USER;
        this.heartbeatFailures = meterRegistry.counter("noviis.sse.heartbeat.failures");
        Gauge.builder("noviis.sse.connections.active", this, NotificationSseEmitterRegistry::activeConnections)
                .register(meterRegistry);
        Gauge.builder("noviis.sse.heartbeat.gap", this, NotificationSseEmitterRegistry::heartbeatGapSeconds)
                .baseUnit("seconds")
                .register(meterRegistry);
    }

    NotificationSseEmitterRegistry(long timeoutMillis, int maxConnectionsPerUser) {
        this(timeoutMillis, maxConnectionsPerUser, Metrics.globalRegistry);
    }

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = createEmitter();
        String connectionId = UUID.randomUUID().toString();
        EmitterConnection connection = new EmitterConnection(emitter, connectionSequence.incrementAndGet());
        List<SseEmitter> evictedEmitters;

        while (true) {
            Object lock = lockFor(userId);
            synchronized (lock) {
                if (userLocks.get(userId) != lock) {
                    continue;
                }
                Map<String, EmitterConnection> userEmitters = emitters.computeIfAbsent(
                        userId,
                        ignored -> new ConcurrentHashMap<>());
                userEmitters.put(connectionId, connection);
                evictedEmitters = enforceConnectionLimit(userEmitters, connectionId);
                break;
            }
        }

        evictedEmitters.forEach(SseEmitter::complete);

        emitter.onCompletion(() -> removeEmitter(userId, connectionId));
        emitter.onTimeout(() -> removeEmitter(userId, connectionId));
        emitter.onError((e) -> removeEmitter(userId, connectionId));

        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("connected!"));
        } catch (IOException | RuntimeException e) {
            completeWithError(userId, connectionId, emitter, e);
        }

        return emitter;
    }

    @Scheduled(fixedRate = 25_000, scheduler = "heartbeatTaskScheduler")
    public void sendHeartbeat() {
        lastHeartbeatMillis.set(System.currentTimeMillis());
        if (emitters.isEmpty()) {
            return;
        }

        for (Long userId : new ArrayList<>(emitters.keySet())) {
            Map<String, EmitterConnection> userEmitters = emitters.get(userId);
            if (userEmitters == null || userEmitters.isEmpty()) {
                removeEmptyUser(userId, userEmitters);
                continue;
            }

            for (Map.Entry<String, EmitterConnection> entry : new ArrayList<>(userEmitters.entrySet())) {
                try {
                    entry.getValue().emitter().send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException | RuntimeException e) {
                    heartbeatFailures.increment();
                    completeWithError(userId, entry.getKey(), entry.getValue().emitter(), e);
                }
            }
        }
    }

    @Override
    public void publish(Long userId, NotificationResponse.NotificationSummary summary) {
        Map<String, EmitterConnection> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }

        for (Map.Entry<String, EmitterConnection> entry : new ArrayList<>(userEmitters.entrySet())) {
            try {
                entry.getValue().emitter().send(SseEmitter.event()
                        .name("notification")
                        .data(summary));
            } catch (IOException | RuntimeException e) {
                completeWithError(userId, entry.getKey(), entry.getValue().emitter(), e);
            }
        }
    }

    public void subscribeCommentTopic(Long userId, Long postId, String subscriberId) {
        commentSubscribers
                .computeIfAbsent(postId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>())
                .put(subscriberId, Boolean.TRUE);
    }

    public void unsubscribeCommentTopic(Long userId, Long postId, String subscriberId) {
        ConcurrentMap<Long, ConcurrentMap<String, Boolean>> postSubscribers = commentSubscribers.get(postId);
        if (postSubscribers == null) {
            return;
        }

        ConcurrentMap<String, Boolean> userSubscribers = postSubscribers.get(userId);
        if (userSubscribers == null) {
            removeEmptyCommentTopic(postId, postSubscribers);
            return;
        }

        userSubscribers.remove(subscriberId);
        if (userSubscribers.isEmpty()) {
            postSubscribers.remove(userId, userSubscribers);
            removeEmptyCommentTopic(postId, postSubscribers);
        }
    }

    @Override
    public void publishCommentEvent(CommentStreamEvent event) {
        ConcurrentMap<Long, ConcurrentMap<String, Boolean>> postSubscribers = commentSubscribers.get(event.getPostId());
        if (postSubscribers == null || postSubscribers.isEmpty()) {
            return;
        }

        for (Long userId : new ArrayList<>(postSubscribers.keySet())) {
            publishCommentEventToUser(userId, event);
        }
    }

    SseEmitter createEmitter() {
        return new SseEmitter(timeoutMillis);
    }

    private void publishCommentEventToUser(Long userId, CommentStreamEvent event) {
        Map<String, EmitterConnection> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }

        for (Map.Entry<String, EmitterConnection> entry : new ArrayList<>(userEmitters.entrySet())) {
            try {
                entry.getValue().emitter().send(SseEmitter.event()
                        .name("comment")
                        .data(event));
            } catch (IOException | RuntimeException e) {
                completeWithError(userId, entry.getKey(), entry.getValue().emitter(), e);
            }
        }
    }

    private void removeEmptyCommentTopic(
            Long postId,
            ConcurrentMap<Long, ConcurrentMap<String, Boolean>> expectedPostSubscribers) {
        if (expectedPostSubscribers.isEmpty()) {
            commentSubscribers.remove(postId, expectedPostSubscribers);
        }
    }

    private List<SseEmitter> enforceConnectionLimit(
            Map<String, EmitterConnection> userEmitters,
            String newConnectionId) {
        List<SseEmitter> evictedEmitters = new ArrayList<>();

        while (userEmitters.size() > maxConnectionsPerUser) {
            String oldestConnectionId = userEmitters.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(newConnectionId))
                    .min(Comparator.comparingLong(entry -> entry.getValue().createdOrder()))
                    .map(Map.Entry::getKey)
                    .orElse(null);

            if (oldestConnectionId == null) {
                return evictedEmitters;
            }

            EmitterConnection removed = userEmitters.remove(oldestConnectionId);
            if (removed != null) {
                evictedEmitters.add(removed.emitter());
            }
        }

        return evictedEmitters;
    }

    private void removeEmitter(Long userId, String connectionId) {
        Object lock = userLocks.get(userId);
        if (lock == null) {
            return;
        }
        synchronized (lock) {
            if (userLocks.get(userId) != lock) {
                return;
            }
            Map<String, EmitterConnection> userEmitters = emitters.get(userId);
            if (userEmitters == null) {
                userLocks.remove(userId, lock);
                return;
            }

            userEmitters.remove(connectionId);
            if (userEmitters.isEmpty()) {
                emitters.remove(userId, userEmitters);
                userLocks.remove(userId, lock);
            }
        }
    }

    private Object lockFor(Long userId) {
        return userLocks.computeIfAbsent(userId, ignored -> new Object());
    }

    private void completeWithError(Long userId, String connectionId, SseEmitter emitter, Throwable error) {
        removeEmitter(userId, connectionId);
        try {
            emitter.completeWithError(error);
        } catch (RuntimeException completeError) {
            log.debug("Failed to complete SSE emitter with error: userId={}, connectionId={}",
                    userId,
                    connectionId,
                    completeError);
        }
    }

    private void removeEmptyUser(Long userId, Map<String, EmitterConnection> expectedEmitters) {
        Object lock = userLocks.get(userId);
        if (lock == null) {
            if (expectedEmitters != null && expectedEmitters.isEmpty()) {
                emitters.computeIfPresent(userId, (currentUserId, currentEmitters) -> {
                    if (currentEmitters == expectedEmitters
                            && currentEmitters.isEmpty()
                            && userLocks.get(currentUserId) == null) {
                        return null;
                    }
                    return currentEmitters;
                });
            }
            return;
        }
        synchronized (lock) {
            if (userLocks.get(userId) != lock) {
                return;
            }
            Map<String, EmitterConnection> userEmitters = emitters.get(userId);
            if (userEmitters == null) {
                userLocks.remove(userId, lock);
            } else if (userEmitters == expectedEmitters && userEmitters.isEmpty()) {
                emitters.remove(userId, userEmitters);
                userLocks.remove(userId, lock);
            }
        }
    }

    private double activeConnections() {
        return emitters.values().stream().mapToLong(Map::size).sum();
    }

    private double heartbeatGapSeconds() {
        long lastHeartbeat = lastHeartbeatMillis.get();
        return lastHeartbeat == 0L ? 0.0 : Math.max(0L, System.currentTimeMillis() - lastHeartbeat) / 1_000.0;
    }

    private record EmitterConnection(SseEmitter emitter, long createdOrder) {
    }
}
