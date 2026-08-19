package com.weedrice.whiteboard.domain.notification.web;

import com.weedrice.whiteboard.domain.notification.dto.CommentStreamEvent;
import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.config.NotificationStreamProperties;
import com.weedrice.whiteboard.domain.notification.service.CommentStreamPublisher;
import com.weedrice.whiteboard.domain.notification.service.NotificationStreamPublisher;
import com.weedrice.whiteboard.domain.notification.service.NotificationStreamControl;
import com.weedrice.whiteboard.domain.shop.event.ShopItemSaleStatusChangedEvent;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class NotificationSseEmitterRegistry
        implements NotificationStreamPublisher, CommentStreamPublisher, NotificationStreamControl {

    private static final long DEFAULT_TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();
    private static final int DEFAULT_MAX_CONNECTIONS_PER_USER = 5;
    private final Map<Long, Map<String, EmitterConnection>> emitters = new ConcurrentHashMap<>();
    private final Map<Long, Object> userLocks = new ConcurrentHashMap<>();
    private final AtomicLong connectionSequence = new AtomicLong();
    private final AtomicLong lastHeartbeatMillis = new AtomicLong();
    private final NotificationStreamProperties properties;
    private final CommentTopicSubscriptionRegistry commentTopics;
    private final Counter heartbeatFailures;

    @Autowired
    public NotificationSseEmitterRegistry(
            NotificationStreamProperties properties,
            MeterRegistry meterRegistry,
            CommentTopicSubscriptionRegistry commentTopics) {
        this.properties = properties;
        this.commentTopics = commentTopics;
        this.heartbeatFailures = meterRegistry.counter("noviis.sse.heartbeat.failures");
        Gauge.builder("noviis.sse.connections.active", this, NotificationSseEmitterRegistry::activeConnections)
                .register(meterRegistry);
        Gauge.builder("noviis.sse.heartbeat.gap", this, NotificationSseEmitterRegistry::heartbeatGapSeconds)
                .baseUnit("seconds")
                .register(meterRegistry);
    }

    NotificationSseEmitterRegistry(
            NotificationStreamProperties properties,
            MeterRegistry meterRegistry) {
        this(properties, meterRegistry, new CommentTopicSubscriptionRegistry(meterRegistry));
    }

    public SseEmitter subscribe(Long userId, UUID sessionFamilyId) {
        if (sessionFamilyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        SseEmitter emitter = createEmitter();
        String connectionId = UUID.randomUUID().toString();
        EmitterConnection connection = new EmitterConnection(
                emitter,
                connectionSequence.incrementAndGet(),
                sessionFamilyId);
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
                evictedEmitters = enforceConnectionLimit(userId, userEmitters, connectionId);
                break;
            }
        }

        evictedEmitters.forEach(SseEmitter::complete);

        emitter.onCompletion(() -> removeEmitter(userId, connectionId));
        emitter.onTimeout(() -> removeEmitter(userId, connectionId));
        emitter.onError((e) -> removeEmitter(userId, connectionId));

        try {
            emitter.send(SseEmitter.event()
                    .name(NotificationSseEvents.CONNECT)
                    .id(connectionId)
                    .data(connectionId));
        } catch (IOException | RuntimeException e) {
            completeWithError(userId, connectionId, emitter, e);
        }

        return emitter;
    }

    SseEmitter subscribe(Long userId) {
        return subscribe(userId, new UUID(0L, 0L));
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
                        .name(NotificationSseEvents.NOTIFICATION)
                        .data(summary));
            } catch (IOException | RuntimeException e) {
                completeWithError(userId, entry.getKey(), entry.getValue().emitter(), e);
            }
        }
    }

    public void subscribeCommentTopic(Long userId, Long boardId, Long postId, String subscriberId) {
        Object lock = userLocks.get(userId);
        if (lock == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        synchronized (lock) {
            Map<String, EmitterConnection> userEmitters = emitters.get(userId);
            if (userLocks.get(userId) != lock || userEmitters == null || userEmitters.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            if (!userEmitters.containsKey(subscriberId)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }

            commentTopics.register(userId, boardId, postId, subscriberId);
        }
    }

    public void publishShopItemSaleStatusChanged(ShopItemSaleStatusChangedEvent event) {
        if (event == null || emitters.isEmpty()) {
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
                    entry.getValue().emitter().send(SseEmitter.event()
                            .name(NotificationSseEvents.SHOP_ITEM_SALE_STATUS_CHANGED)
                            .data(event));
                } catch (IOException | RuntimeException exception) {
                    completeWithError(userId, entry.getKey(), entry.getValue().emitter(), exception);
                }
            }
        }
    }

    public void unsubscribeCommentTopic(Long userId, Long postId, String subscriberId) {
        commentTopics.unsubscribe(userId, postId, subscriberId);
    }

    @Override
    public void publishCommentEvent(CommentStreamEvent event) {
        Map<Long, java.util.Set<String>> postSubscribers = commentTopics.subscriptionsForPost(event.getPostId());
        if (postSubscribers.isEmpty()) {
            return;
        }

        postSubscribers.forEach((userId, connectionIds) ->
                publishCommentEventToUser(userId, connectionIds, event));
    }

    SseEmitter createEmitter() {
        return new SseEmitter(resolveTimeoutMillis());
    }

    private void publishCommentEventToUser(
            Long userId,
            java.util.Set<String> subscribedConnectionIds,
            CommentStreamEvent event) {
        Map<String, EmitterConnection> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }

        if (subscribedConnectionIds == null || subscribedConnectionIds.isEmpty()) {
            return;
        }

        for (Map.Entry<String, EmitterConnection> entry : new ArrayList<>(userEmitters.entrySet())) {
            if (!subscribedConnectionIds.contains(entry.getKey())) {
                continue;
            }
            try {
                entry.getValue().emitter().send(SseEmitter.event()
                        .name(NotificationSseEvents.COMMENT)
                        .data(event));
            } catch (IOException | RuntimeException e) {
                completeWithError(userId, entry.getKey(), entry.getValue().emitter(), e);
            }
        }
    }

    private List<SseEmitter> enforceConnectionLimit(
            Long userId,
            Map<String, EmitterConnection> userEmitters,
            String newConnectionId) {
        List<SseEmitter> evictedEmitters = new ArrayList<>();

        while (userEmitters.size() > resolveMaxConnectionsPerUser()) {
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
                commentTopics.removeConnection(userId, oldestConnectionId);
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
            commentTopics.removeConnection(userId, connectionId);
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

    @Override
    public void disconnectUser(Long userId) {
        if (userId == null) {
            return;
        }
        List<SseEmitter> disconnectedEmitters;
        while (true) {
            Object lock = lockFor(userId);
            synchronized (lock) {
                if (userLocks.get(userId) != lock) {
                    continue;
                }
                Map<String, EmitterConnection> removed = emitters.remove(userId);
                disconnectedEmitters = removed == null
                        ? List.of()
                        : removed.values().stream().map(EmitterConnection::emitter).toList();
                invalidateCommentTopicsForUser(userId);
                userLocks.remove(userId, lock);
                break;
            }
        }
        disconnectedEmitters.forEach(emitter -> {
            try {
                emitter.complete();
            } catch (RuntimeException ex) {
                log.debug("Failed to complete revoked SSE emitter: userId={}", userId, ex);
            }
        });
    }

    @Override
    public void disconnectSessionFamily(Long userId, UUID sessionFamilyId) {
        if (userId == null || sessionFamilyId == null) {
            return;
        }
        disconnectConnections(userId, connection -> sessionFamilyId.equals(connection.sessionFamilyId()));
    }

    @Override
    public void disconnectOtherSessionFamilies(Long userId, UUID retainedSessionFamilyId) {
        if (userId == null) {
            return;
        }
        disconnectConnections(
                userId,
                connection -> !java.util.Objects.equals(retainedSessionFamilyId, connection.sessionFamilyId()));
    }

    private void disconnectConnections(
            Long userId,
            java.util.function.Predicate<EmitterConnection> shouldDisconnect) {
        List<SseEmitter> disconnectedEmitters = new ArrayList<>();
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
            for (Map.Entry<String, EmitterConnection> entry : new ArrayList<>(userEmitters.entrySet())) {
                if (!shouldDisconnect.test(entry.getValue())) {
                    continue;
                }
                if (userEmitters.remove(entry.getKey(), entry.getValue())) {
                    commentTopics.removeConnection(userId, entry.getKey());
                    disconnectedEmitters.add(entry.getValue().emitter());
                }
            }
            if (userEmitters.isEmpty()) {
                emitters.remove(userId, userEmitters);
                userLocks.remove(userId, lock);
            }
        }
        disconnectedEmitters.forEach(emitter -> {
            try {
                emitter.complete();
            } catch (RuntimeException exception) {
                log.debug("Failed to complete revoked session SSE emitter: userId={}", userId, exception);
            }
        });
    }

    @Override
    public void invalidateCommentTopic(Long postId) {
        if (postId == null) {
            return;
        }
        Map<Long, java.util.Set<String>> removed = commentTopics.invalidatePost(postId);
        if (!removed.isEmpty()) {
            publishCommentTopicAccessRevoked(removed, Map.of("postId", postId));
        }
    }


    @Override
    public void invalidateCommentTopicsForBoard(Long boardId) {
        if (boardId == null) {
            return;
        }
        CommentTopicSubscriptionRegistry.BoardInvalidation invalidation =
                commentTopics.invalidateBoard(boardId);
        invalidation.affectedConnections().forEach((userId, connectionIds) ->
                publishCommentTopicInvalidation(userId, connectionIds, boardId));
    }

    private void publishCommentTopicInvalidation(Long userId, java.util.Set<String> connectionIds, Long boardId) {
        Map<String, EmitterConnection> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }
        for (Map.Entry<String, EmitterConnection> entry : new ArrayList<>(userEmitters.entrySet())) {
            if (!connectionIds.contains(entry.getKey())) {
                continue;
            }
            try {
                entry.getValue().emitter().send(SseEmitter.event()
                        .name(NotificationSseEvents.COMMENT_TOPIC_INVALIDATED)
                        .data(Map.of("boardId", boardId)));
            } catch (IOException | RuntimeException exception) {
                completeWithError(userId, entry.getKey(), entry.getValue().emitter(), exception);
            }
        }
    }

    @Override
    public void invalidateCommentTopicsForUser(Long userId) {
        if (userId == null) {
            return;
        }
        java.util.Set<String> affectedConnections = new java.util.HashSet<>();
        Map<String, EmitterConnection> userEmitters = emitters.get(userId);
        if (userEmitters != null) {
            affectedConnections.addAll(userEmitters.keySet());
        }
        affectedConnections.addAll(commentTopics.invalidateUser(userId));
        if (!affectedConnections.isEmpty()) {
            publishControlEvent(userId, affectedConnections, NotificationSseEvents.COMMENT_TOPIC_ACCESS_REVOKED, Map.of("reason", "access-revoked"));
        }
    }

    private void publishCommentTopicAccessRevoked(
            Map<Long, java.util.Set<String>> removed,
            Map<String, Object> data) {
        removed.forEach((userId, connectionIds) ->
                publishControlEvent(userId, connectionIds, NotificationSseEvents.COMMENT_TOPIC_ACCESS_REVOKED, data));
    }

    private void publishControlEvent(
            Long userId,
            java.util.Set<String> connectionIds,
            String eventName,
            Map<String, Object> data) {
        // 이름을 인자로 받는 유일한 경로라 원본 스캔이 들여다볼 수 없다. 런타임에서 막는다.
        if (!NotificationSseEvents.ALL.contains(eventName)) {
            throw new IllegalArgumentException(
                    "NotificationSseEvents에 등재되지 않은 SSE 이벤트 이름: " + eventName);
        }

        Map<String, EmitterConnection> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }
        for (Map.Entry<String, EmitterConnection> entry : new ArrayList<>(userEmitters.entrySet())) {
            if (!connectionIds.contains(entry.getKey())) {
                continue;
            }
            try {
                entry.getValue().emitter().send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | RuntimeException exception) {
                completeWithError(userId, entry.getKey(), entry.getValue().emitter(), exception);
            }
        }
    }

    int commentTopicCount() {
        return commentTopics.topicCount();
    }

    boolean hasCommentTopic(Long postId) {
        return commentTopics.hasTopic(postId);
    }

    private long resolveTimeoutMillis() {
        return properties.getTimeoutMillis() > 0 ? properties.getTimeoutMillis() : DEFAULT_TIMEOUT_MILLIS;
    }

    private int resolveMaxConnectionsPerUser() {
        return properties.getMaxConnectionsPerUser() > 0
                ? properties.getMaxConnectionsPerUser()
                : DEFAULT_MAX_CONNECTIONS_PER_USER;
    }

    private record EmitterConnection(SseEmitter emitter, long createdOrder, UUID sessionFamilyId) {
    }
}
