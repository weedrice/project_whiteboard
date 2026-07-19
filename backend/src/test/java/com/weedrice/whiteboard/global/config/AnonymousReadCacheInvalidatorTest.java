package com.weedrice.whiteboard.global.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnonymousReadCacheInvalidatorTest {

    private static final List<String> CACHE_NAMES = List.of(
            CacheNames.BOARD_CATALOG_ANONYMOUS,
            CacheNames.BOARD_DETAIL_ANONYMOUS,
            CacheNames.TRENDING_POSTS_ANONYMOUS,
            CacheNames.HOME_LANDING_ANONYMOUS);

    @AfterEach
    void tearDownTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void clearsCachesOnlyAfterTransactionCommit() {
        ConcurrentMapCacheManager cacheManager = populatedCacheManager();
        AnonymousReadCacheInvalidator invalidator = new AnonymousReadCacheInvalidator(cacheManager);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        invalidator.evictBoardRelatedCachesAfterCommit();

        assertCachesPopulated(cacheManager);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        CACHE_NAMES.forEach(cacheName -> assertThat(cacheManager.getCache(cacheName).get("key")).isNull());
    }

    @Test
    void clearsCachesImmediatelyOutsideTransaction() {
        ConcurrentMapCacheManager cacheManager = populatedCacheManager();
        AnonymousReadCacheInvalidator invalidator = new AnonymousReadCacheInvalidator(cacheManager);

        invalidator.evictBoardRelatedCachesAfterCommit();

        CACHE_NAMES.forEach(cacheName -> assertThat(cacheManager.getCache(cacheName).get("key")).isNull());
    }

    @Test
    void postInvalidationClearsBoardMetadataThatContainsPostSummariesAndCounts() {
        ConcurrentMapCacheManager cacheManager = populatedCacheManager();
        AnonymousReadCacheInvalidator invalidator = new AnonymousReadCacheInvalidator(cacheManager);

        invalidator.evictPostRelatedCachesAfterCommit();

        assertThat(cacheManager.getCache(CacheNames.TRENDING_POSTS_ANONYMOUS).get("key")).isNull();
        assertThat(cacheManager.getCache(CacheNames.HOME_LANDING_ANONYMOUS).get("key")).isNull();
        assertThat(cacheManager.getCache(CacheNames.BOARD_CATALOG_ANONYMOUS).get("key")).isNull();
        assertThat(cacheManager.getCache(CacheNames.BOARD_DETAIL_ANONYMOUS).get("key")).isNull();
    }

    @Test
    void engagementInvalidationClearsOnlyFeedCachesAndAffectedBoardDetail() {
        ConcurrentMapCacheManager cacheManager = populatedCacheManager();
        cacheManager.getCache(CacheNames.BOARD_DETAIL_ANONYMOUS).put("other", "value");
        AnonymousReadCacheInvalidator invalidator = new AnonymousReadCacheInvalidator(cacheManager);

        invalidator.evictPostEngagementCachesAfterCommit("key");

        assertThat(cacheManager.getCache(CacheNames.TRENDING_POSTS_ANONYMOUS).get("key")).isNull();
        assertThat(cacheManager.getCache(CacheNames.HOME_LANDING_ANONYMOUS).get("key")).isNull();
        assertThat(cacheManager.getCache(CacheNames.BOARD_DETAIL_ANONYMOUS).get("key")).isNull();
        assertThat(cacheManager.getCache(CacheNames.BOARD_DETAIL_ANONYMOUS).get("other")).isNotNull();
        assertThat(cacheManager.getCache(CacheNames.BOARD_CATALOG_ANONYMOUS).get("key")).isNotNull();
    }

    private ConcurrentMapCacheManager populatedCacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(CACHE_NAMES.toArray(String[]::new));
        CACHE_NAMES.forEach(cacheName -> cacheManager.getCache(cacheName).put("key", "value"));
        return cacheManager;
    }

    private void assertCachesPopulated(ConcurrentMapCacheManager cacheManager) {
        CACHE_NAMES.forEach(cacheName -> {
            Cache.ValueWrapper value = cacheManager.getCache(cacheName).get("key");
            assertThat(value).isNotNull();
            assertThat(value.get()).isEqualTo("value");
        });
    }
}
