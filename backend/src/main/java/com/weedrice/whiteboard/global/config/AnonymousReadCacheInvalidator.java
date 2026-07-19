package com.weedrice.whiteboard.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AnonymousReadCacheInvalidator {

    private static final List<String> BOARD_RELATED_CACHES = List.of(
            CacheNames.BOARD_CATALOG_ANONYMOUS,
            CacheNames.BOARD_DETAIL_ANONYMOUS,
            CacheNames.TRENDING_POSTS_ANONYMOUS,
            CacheNames.HOME_LANDING_ANONYMOUS);

    private static final List<String> POST_RELATED_CACHES = BOARD_RELATED_CACHES;
    private static final List<String> POST_ENGAGEMENT_CACHES = List.of(
            CacheNames.TRENDING_POSTS_ANONYMOUS,
            CacheNames.HOME_LANDING_ANONYMOUS);

    private final CacheManager cacheManager;

    public void evictBoardRelatedCachesAfterCommit() {
        evictAfterCommit(BOARD_RELATED_CACHES);
    }

    public void evictPostRelatedCachesAfterCommit() {
        evictAfterCommit(POST_RELATED_CACHES);
    }

    public void evictPostEngagementCachesAfterCommit(String boardUrl) {
        runAfterCommit(() -> {
            evictCaches(POST_ENGAGEMENT_CACHES);
            evictCacheEntry(CacheNames.BOARD_DETAIL_ANONYMOUS, boardUrl);
        });
    }

    public void evictBoardSubscriptionCachesAfterCommit(String boardUrl) {
        runAfterCommit(() -> {
            evictCaches(List.of(CacheNames.BOARD_CATALOG_ANONYMOUS));
            evictCacheEntry(CacheNames.BOARD_DETAIL_ANONYMOUS, boardUrl);
        });
    }

    public void evictAuthorProjectionCachesAfterCommit() {
        evictAfterCommit(POST_RELATED_CACHES);
    }

    private void evictAfterCommit(List<String> cacheNames) {
        runAfterCommit(() -> evictCaches(cacheNames));
    }

    private void runAfterCommit(Runnable invalidation) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidation.run();
                }
            });
            return;
        }
        invalidation.run();
    }

    private void evictCaches(List<String> cacheNames) {
        cacheNames.forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });
    }

    private void evictCacheEntry(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null && key != null) {
            cache.evict(key);
        }
    }
}
