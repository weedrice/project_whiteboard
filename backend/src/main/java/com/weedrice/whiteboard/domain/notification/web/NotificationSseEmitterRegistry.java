package com.weedrice.whiteboard.domain.notification.web;

import com.weedrice.whiteboard.domain.notification.dto.CommentStreamEvent;
import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.config.NotificationStreamProperties;
import com.weedrice.whiteboard.domain.notification.service.CommentStreamPublisher;
import com.weedrice.whiteboard.domain.notification.service.NotificationStreamPublisher;
import com.weedrice.whiteboard.domain.notification.service.NotificationStreamControl;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
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
public class NotificationSseEmitterRegistry
        implements NotificationStreamPublisher, CommentStreamPublisher, NotificationStreamControl {

    private static final long DEFAULT_TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();
    private static final int DEFAULT_MAX_CONNECTIONS_PER_USER = 5;
    private static final int MAX_COMMENT_TOPICS_PER_USER = 100;
    private static final int MAX_COMMENT_SUBSCRIBERS_PER_TOPIC = 10;

    private final Map<Long, Map<String, EmitterConnection>> emitters = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, ConcurrentMap<Long, ConcurrentMap<String, Boolean>>> commentSubscribers =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Long> commentTopicBoardIds = new ConcurrentHashMap<>();
    private final Object commentTopicLock = new Object();
    private final Map<Long, Object> userLocks = new ConcurrentHashMap<>();
    private final AtomicLong connectionSequence = new AtomicLong();
    private final AtomicLong lastHeartbeatMillis = new AtomicLong();
    private final NotificationStreamProperties properties;
    private final Counter heartbeatFailures;
    private final Counter commentTopicCleanups;

    public NotificationSseEmitterRegistry(
            NotificationStreamProperties properties,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.heartbeatFailures = meterRegistry.counter("noviis.sse.heartbeat.failures");
        this.commentTopicCleanups = meterRegistry.counter("noviis.sse.comment.topics.cleaned");
        Gauge.builder("noviis.sse.connections.active", this, NotificationSseEmitterRegistry::activeConnections)
                .register(meterRegistry);
        Gauge.builder("noviis.sse.heartbeat.gap", this, NotificationSseEmitterRegistry::heartbeatGapSeconds)
                .baseUnit("seconds")
                .register(meterRegistry);
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
                    .name("connect")
                    .id(connectionId)
                    .data(connectionId));
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

            registerCommentSubscription(userId, boardId, postId, subscriberId);
        }
    }

    private void registerCommentSubscription(Long userId, Long boardId, Long postId, String subscriberId) {
        synchronized (commentTopicLock) {
            Long registeredBoardId = commentTopicBoardIds.putIfAbsent(postId, boardId);
            if (registeredBoardId != null && !registeredBoardId.equals(boardId)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }

            ConcurrentMap<Long, ConcurrentMap<String, Boolean>> postSubscribers =
                    commentSubscribers.computeIfAbsent(postId, ignored -> new ConcurrentHashMap<>());
            ConcurrentMap<String, Boolean> userSubscribers =
                    postSubscribers.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>());
            boolean existingSubscriber = userSubscribers.containsKey(subscriberId);
            if (!existingSubscriber && userSubscribers.size() >= MAX_COMMENT_SUBSCRIBERS_PER_TOPIC) {
                removeEmptyCommentTopic(postId, postSubscribers);
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            if (!existingSubscriber
                    && userSubscribers.isEmpty()
                    && countCommentTopicsForUser(userId) >= MAX_COMMENT_TOPICS_PER_USER) {
                postSubscribers.remove(userId, userSubscribers);
                removeEmptyCommentTopic(postId, postSubscribers);
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            userSubscribers.put(subscriberId, Boolean.TRUE);
        }
    }

    public void unsubscribeCommentTopic(Long userId, Long postId, String subscriberId) {
        synchronized (commentTopicLock) {
            unsubscribeCommentTopicLocked(userId, postId, subscriberId);
        }
    }

    private void unsubscribeCommentTopicLocked(Long userId, Long postId, String subscriberId) {
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
            publishCommentEventToUser(userId, postSubscribers.get(userId), event);
        }
    }

    SseEmitter createEmitter() {
        return new SseEmitter(resolveTimeoutMillis());
    }

    private void publishCommentEventToUser(
            Long userId,
            ConcurrentMap<String, Boolean> subscribedConnectionIds,
            CommentStreamEvent event) {
        Map<String, EmitterConnection> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }

        if (subscribedConnectionIds == null || subscribedConnectionIds.isEmpty()) {
            return;
        }

        for (Map.Entry<String, EmitterConnection> entry : new ArrayList<>(userEmitters.entrySet())) {
            if (!subscribedConnectionIds.containsKey(entry.getKey())) {
                continue;
            }
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
            if (commentSubscribers.remove(postId, expectedPostSubscribers)) {
                commentTopicBoardIds.remove(postId);
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
                removeCommentSubscriptions(userId, oldestConnectionId);
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
            removeCommentSubscriptions(userId, connectionId);
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
    public void invalidateCommentTopic(Long postId) {
        if (postId == null) {
            return;
        }
        ConcurrentMap<Long, ConcurrentMap<String, Boolean>> removed;
        synchronized (commentTopicLock) {
            removed = commentSubscribers.remove(postId);
            commentTopicBoardIds.remove(postId);
        }
        if (removed != null && !removed.isEmpty()) {
            commentTopicCleanups.increment();
        }
    }


    @Override
    public void invalidateCommentTopicsForBoard(Long boardId) {
        if (boardId == null) {
            return;
        }
        int removedTopics = 0;
        Map<Long, java.util.Set<String>> affectedConnections = new java.util.HashMap<>();
        synchronized (commentTopicLock) {
            for (Map.Entry<Long, Long> entry : new ArrayList<>(commentTopicBoardIds.entrySet())) {
                if (!boardId.equals(entry.getValue())) {
                    continue;
                }
                Long postId = entry.getKey();
                ConcurrentMap<Long, ConcurrentMap<String, Boolean>> removed = commentSubscribers.remove(postId);
                commentTopicBoardIds.remove(postId, boardId);
                if (removed != null && !removed.isEmpty()) {
                    removed.forEach((userId, connectionIds) -> affectedConnections
                            .computeIfAbsent(userId, ignored -> new java.util.HashSet<>())
                            .addAll(connectionIds.keySet()));
                    removedTopics++;
                }
            }
        }
        if (removedTopics > 0) {
            commentTopicCleanups.increment(removedTopics);
        }
        affectedConnections.forEach((userId, connectionIds) ->
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
                        .name("comment-topic-invalidated")
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
        int removedTopics = 0;
        synchronized (commentTopicLock) {
            for (Map.Entry<Long, ConcurrentMap<Long, ConcurrentMap<String, Boolean>>> entry
                    : new ArrayList<>(commentSubscribers.entrySet())) {
                ConcurrentMap<Long, ConcurrentMap<String, Boolean>> postSubscribers = entry.getValue();
                ConcurrentMap<String, Boolean> removed = postSubscribers.remove(userId);
                if (removed != null && !removed.isEmpty()) {
                    removedTopics++;
                }
                removeEmptyCommentTopic(entry.getKey(), postSubscribers);
            }
        }
        if (removedTopics > 0) {
            commentTopicCleanups.increment(removedTopics);
        }
    }

    private long countCommentTopicsForUser(Long userId) {
        return commentSubscribers.values().stream()
                .filter(postSubscribers -> {
                    ConcurrentMap<String, Boolean> subscribers = postSubscribers.get(userId);
                    return subscribers != null && !subscribers.isEmpty();
                })
                .count();
    }

    private void removeCommentSubscriptions(Long userId, String connectionId) {
        int removedTopics = 0;
        synchronized (commentTopicLock) {
            for (Map.Entry<Long, ConcurrentMap<Long, ConcurrentMap<String, Boolean>>> entry
                    : new ArrayList<>(commentSubscribers.entrySet())) {
                ConcurrentMap<Long, ConcurrentMap<String, Boolean>> postSubscribers = entry.getValue();
                ConcurrentMap<String, Boolean> userSubscribers = postSubscribers.get(userId);
                if (userSubscribers != null && userSubscribers.remove(connectionId) != null) {
                    if (userSubscribers.isEmpty()) {
                        postSubscribers.remove(userId, userSubscribers);
                    }
                    removedTopics++;
                }
                removeEmptyCommentTopic(entry.getKey(), postSubscribers);
            }
        }
        if (removedTopics > 0) {
            commentTopicCleanups.increment(removedTopics);
        }
    }

    private long resolveTimeoutMillis() {
        return properties.getTimeoutMillis() > 0 ? properties.getTimeoutMillis() : DEFAULT_TIMEOUT_MILLIS;
    }

    private int resolveMaxConnectionsPerUser() {
        return properties.getMaxConnectionsPerUser() > 0
                ? properties.getMaxConnectionsPerUser()
                : DEFAULT_MAX_CONNECTIONS_PER_USER;
    }

    private record EmitterConnection(SseEmitter emitter, long createdOrder) {
    }
}
