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

    private final CacheManager cacheManager;

    public void evictBoardRelatedCachesAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictBoardRelatedCaches();
                }
            });
            return;
        }
        evictBoardRelatedCaches();
    }

    private void evictBoardRelatedCaches() {
        BOARD_RELATED_CACHES.forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });
    }
}
