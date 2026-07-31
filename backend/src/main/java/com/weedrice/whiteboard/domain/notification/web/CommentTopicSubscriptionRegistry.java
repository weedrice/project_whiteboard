package com.weedrice.whiteboard.domain.notification.web;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
final class CommentTopicSubscriptionRegistry {

    private static final int MAX_TOPICS_PER_USER = 100;
    private static final int MAX_SUBSCRIBERS_PER_TOPIC = 10;

    private final ConcurrentMap<Long, ConcurrentMap<Long, ConcurrentMap<String, Boolean>>> subscribers =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Long> topicBoardIds = new ConcurrentHashMap<>();
    private final Object lock = new Object();
    private final Counter cleanups;

    CommentTopicSubscriptionRegistry(MeterRegistry meterRegistry) {
        this.cleanups = meterRegistry.counter("noviis.sse.comment.topics.cleaned");
    }

    void register(Long userId, Long boardId, Long postId, String connectionId) {
        synchronized (lock) {
            Long registeredBoardId = topicBoardIds.putIfAbsent(postId, boardId);
            if (registeredBoardId != null && !registeredBoardId.equals(boardId)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }

            ConcurrentMap<Long, ConcurrentMap<String, Boolean>> postSubscribers =
                    subscribers.computeIfAbsent(postId, ignored -> new ConcurrentHashMap<>());
            ConcurrentMap<String, Boolean> userSubscribers =
                    postSubscribers.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>());
            boolean existingSubscriber = userSubscribers.containsKey(connectionId);
            if (!existingSubscriber && userSubscribers.size() >= MAX_SUBSCRIBERS_PER_TOPIC) {
                removeEmptyTopic(postId, postSubscribers);
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            if (!existingSubscriber
                    && userSubscribers.isEmpty()
                    && countTopicsForUser(userId) >= MAX_TOPICS_PER_USER) {
                postSubscribers.remove(userId, userSubscribers);
                removeEmptyTopic(postId, postSubscribers);
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            userSubscribers.put(connectionId, Boolean.TRUE);
        }
    }

    void unsubscribe(Long userId, Long postId, String connectionId) {
        synchronized (lock) {
            ConcurrentMap<Long, ConcurrentMap<String, Boolean>> postSubscribers = subscribers.get(postId);
            if (postSubscribers == null) {
                return;
            }
            ConcurrentMap<String, Boolean> userSubscribers = postSubscribers.get(userId);
            if (userSubscribers == null) {
                removeEmptyTopic(postId, postSubscribers);
                return;
            }

            userSubscribers.remove(connectionId);
            if (userSubscribers.isEmpty()) {
                postSubscribers.remove(userId, userSubscribers);
                removeEmptyTopic(postId, postSubscribers);
            }
        }
    }

    Map<Long, Set<String>> subscriptionsForPost(Long postId) {
        ConcurrentMap<Long, ConcurrentMap<String, Boolean>> postSubscribers = subscribers.get(postId);
        if (postSubscribers == null || postSubscribers.isEmpty()) {
            return Map.of();
        }
        Map<Long, Set<String>> snapshot = new HashMap<>();
        postSubscribers.forEach((userId, connectionIds) -> {
            if (connectionIds != null && !connectionIds.isEmpty()) {
                snapshot.put(userId, Set.copyOf(connectionIds.keySet()));
            }
        });
        return Map.copyOf(snapshot);
    }

    Map<Long, Set<String>> invalidatePost(Long postId) {
        ConcurrentMap<Long, ConcurrentMap<String, Boolean>> removed;
        synchronized (lock) {
            removed = subscribers.remove(postId);
            topicBoardIds.remove(postId);
        }
        Map<Long, Set<String>> snapshot = snapshot(removed);
        if (!snapshot.isEmpty()) {
            cleanups.increment();
        }
        return snapshot;
    }

    BoardInvalidation invalidateBoard(Long boardId) {
        int removedTopics = 0;
        Map<Long, Set<String>> affectedConnections = new HashMap<>();
        synchronized (lock) {
            for (Map.Entry<Long, Long> entry : new ArrayList<>(topicBoardIds.entrySet())) {
                if (!boardId.equals(entry.getValue())) {
                    continue;
                }
                Long postId = entry.getKey();
                ConcurrentMap<Long, ConcurrentMap<String, Boolean>> removed = subscribers.remove(postId);
                topicBoardIds.remove(postId, boardId);
                if (removed != null && !removed.isEmpty()) {
                    removed.forEach((userId, connectionIds) -> affectedConnections
                            .computeIfAbsent(userId, ignored -> new HashSet<>())
                            .addAll(connectionIds.keySet()));
                    removedTopics++;
                }
            }
        }
        if (removedTopics > 0) {
            cleanups.increment(removedTopics);
        }
        return new BoardInvalidation(Map.copyOf(affectedConnections));
    }

    Set<String> invalidateUser(Long userId) {
        int removedTopics = 0;
        Set<String> affectedConnections = new HashSet<>();
        synchronized (lock) {
            for (Map.Entry<Long, ConcurrentMap<Long, ConcurrentMap<String, Boolean>>> entry
                    : new ArrayList<>(subscribers.entrySet())) {
                ConcurrentMap<Long, ConcurrentMap<String, Boolean>> postSubscribers = entry.getValue();
                ConcurrentMap<String, Boolean> removed = postSubscribers.remove(userId);
                if (removed != null && !removed.isEmpty()) {
                    removedTopics++;
                    affectedConnections.addAll(removed.keySet());
                }
                removeEmptyTopic(entry.getKey(), postSubscribers);
            }
        }
        if (removedTopics > 0) {
            cleanups.increment(removedTopics);
        }
        return Set.copyOf(affectedConnections);
    }

    void removeConnection(Long userId, String connectionId) {
        int removedTopics = 0;
        synchronized (lock) {
            for (Map.Entry<Long, ConcurrentMap<Long, ConcurrentMap<String, Boolean>>> entry
                    : new ArrayList<>(subscribers.entrySet())) {
                ConcurrentMap<Long, ConcurrentMap<String, Boolean>> postSubscribers = entry.getValue();
                ConcurrentMap<String, Boolean> userSubscribers = postSubscribers.get(userId);
                if (userSubscribers != null && userSubscribers.remove(connectionId) != null) {
                    if (userSubscribers.isEmpty()) {
                        postSubscribers.remove(userId, userSubscribers);
                    }
                    removedTopics++;
                }
                removeEmptyTopic(entry.getKey(), postSubscribers);
            }
        }
        if (removedTopics > 0) {
            cleanups.increment(removedTopics);
        }
    }

    int topicCount() {
        return subscribers.size();
    }

    boolean hasTopic(Long postId) {
        return subscribers.containsKey(postId);
    }

    private long countTopicsForUser(Long userId) {
        return subscribers.values().stream()
                .filter(postSubscribers -> {
                    ConcurrentMap<String, Boolean> userSubscribers = postSubscribers.get(userId);
                    return userSubscribers != null && !userSubscribers.isEmpty();
                })
                .count();
    }

    private void removeEmptyTopic(
            Long postId,
            ConcurrentMap<Long, ConcurrentMap<String, Boolean>> expectedPostSubscribers) {
        if (expectedPostSubscribers.isEmpty()
                && subscribers.remove(postId, expectedPostSubscribers)) {
            topicBoardIds.remove(postId);
        }
    }

    private Map<Long, Set<String>> snapshot(
            ConcurrentMap<Long, ConcurrentMap<String, Boolean>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<Long, Set<String>> snapshot = new HashMap<>();
        source.forEach((userId, connectionIds) ->
                snapshot.put(userId, Set.copyOf(connectionIds.keySet())));
        return Map.copyOf(snapshot);
    }

    record BoardInvalidation(Map<Long, Set<String>> affectedConnections) {
    }
}
