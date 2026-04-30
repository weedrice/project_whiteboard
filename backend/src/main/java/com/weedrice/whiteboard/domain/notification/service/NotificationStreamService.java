package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
class NotificationStreamService {

    private static final long DEFAULT_TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();
    private static final int DEFAULT_MAX_CONNECTIONS_PER_USER = 5;

    private final Map<Long, Map<String, EmitterConnection>> emitters = new ConcurrentHashMap<>();
    private final Map<Long, Object> userLocks = new ConcurrentHashMap<>();
    private final AtomicLong connectionSequence = new AtomicLong();
    private final long timeoutMillis;
    private final int maxConnectionsPerUser;

    NotificationStreamService(
            @Value("${notification.stream.timeout-millis:1800000}") long timeoutMillis,
            @Value("${notification.stream.max-connections-per-user:5}") int maxConnectionsPerUser) {
        this.timeoutMillis = timeoutMillis > 0 ? timeoutMillis : DEFAULT_TIMEOUT_MILLIS;
        this.maxConnectionsPerUser = maxConnectionsPerUser > 0
                ? maxConnectionsPerUser
                : DEFAULT_MAX_CONNECTIONS_PER_USER;
    }

    SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        String connectionId = UUID.randomUUID().toString();
        EmitterConnection connection = new EmitterConnection(emitter, connectionSequence.incrementAndGet());
        List<SseEmitter> evictedEmitters;

        Object lock = lockFor(userId);
        synchronized (lock) {
            Map<String, EmitterConnection> userEmitters = emitters.computeIfAbsent(
                    userId,
                    ignored -> new ConcurrentHashMap<>());
            userEmitters.put(connectionId, connection);
            evictedEmitters = enforceConnectionLimit(userEmitters, connectionId);
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
            removeEmitter(userId, connectionId);
        }

        return emitter;
    }

    void sendHeartbeat() {
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
                    removeEmitter(userId, entry.getKey());
                }
            }
        }
    }

    void deliverNotification(Long userId, Notification notification) {
        Map<String, EmitterConnection> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }

        NotificationResponse.NotificationSummary summary = NotificationResponse.NotificationSummary.from(notification);
        for (Map.Entry<String, EmitterConnection> entry : new ArrayList<>(userEmitters.entrySet())) {
            try {
                entry.getValue().emitter().send(SseEmitter.event()
                        .name("notification")
                        .data(summary));
            } catch (IOException | RuntimeException e) {
                removeEmitter(userId, entry.getKey());
            }
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
        Object lock = lockFor(userId);
        synchronized (lock) {
            Map<String, EmitterConnection> userEmitters = emitters.get(userId);
            if (userEmitters == null) {
                return;
            }

            userEmitters.remove(connectionId);
            if (userEmitters.isEmpty()) {
                emitters.remove(userId, userEmitters);
            }
        }
    }

    private Object lockFor(Long userId) {
        return userLocks.computeIfAbsent(userId, ignored -> new Object());
    }

    private void removeEmptyUser(Long userId, Map<String, EmitterConnection> expectedEmitters) {
        Object lock = lockFor(userId);
        synchronized (lock) {
            Map<String, EmitterConnection> userEmitters = emitters.get(userId);
            if (userEmitters == null) {
                emitters.remove(userId);
            } else if (userEmitters == expectedEmitters && userEmitters.isEmpty()) {
                emitters.remove(userId, userEmitters);
            }
        }
    }

    private record EmitterConnection(SseEmitter emitter, long createdOrder) {
    }
}
