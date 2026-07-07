package com.weedrice.whiteboard.global.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.weedrice.whiteboard.global.common.util.ClientMetadataNormalizer;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CounterEventGuard {

    private static final Duration DEDUPLICATION_WINDOW = Duration.ofMinutes(30);
    private static final long MAXIMUM_SIZE = 200_000;

    private final Cache<String, Boolean> recentEvents = Caffeine.newBuilder()
            .expireAfterWrite(DEDUPLICATION_WINDOW)
            .maximumSize(MAXIMUM_SIZE)
            .build();

    public boolean isRecentlyRecorded(@NonNull String eventType, @NonNull Long targetId, Long userId,
            String ipAddress) {
        return recentEvents.getIfPresent(buildKey(eventType, targetId, userId, ipAddress)) != null;
    }

    public boolean tryMarkRecorded(@NonNull String eventType, @NonNull Long targetId, Long userId, String ipAddress) {
        return recentEvents.asMap().putIfAbsent(buildKey(eventType, targetId, userId, ipAddress), true) == null;
    }

    public void markRecorded(@NonNull String eventType, @NonNull Long targetId, Long userId, String ipAddress) {
        recentEvents.put(buildKey(eventType, targetId, userId, ipAddress), true);
    }

    private String buildKey(String eventType, Long targetId, Long userId, String ipAddress) {
        String actorKey = userId != null
                ? "user:" + userId
                : "ip:" + ClientMetadataNormalizer.normalizeIpAddress(ipAddress);
        return eventType + ":" + targetId + ":" + actorKey;
    }
}
