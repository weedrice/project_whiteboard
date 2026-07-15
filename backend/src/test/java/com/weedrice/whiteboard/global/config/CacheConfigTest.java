package com.weedrice.whiteboard.global.config;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigTest {

    private final CacheConfig cacheConfig = new CacheConfig();

    @Test
    void registersBoundedCachesWithPerCacheExpiry() {
        CacheManager cacheManager = cacheConfig.cacheManager();

        assertThat(cacheManager.getCacheNames()).containsExactlyInAnyOrder(
                CacheNames.GLOBAL_CONFIG,
                CacheNames.BOARD_CATALOG_ANONYMOUS,
                CacheNames.BOARD_DETAIL_ANONYMOUS,
                CacheNames.TRENDING_POSTS_ANONYMOUS,
                CacheNames.HOME_LANDING_ANONYMOUS);
        assertPolicy(cacheManager, CacheNames.GLOBAL_CONFIG, 1000, 600);
        assertPolicy(cacheManager, CacheNames.BOARD_CATALOG_ANONYMOUS, 32, 60);
        assertPolicy(cacheManager, CacheNames.BOARD_DETAIL_ANONYMOUS, 500, 30);
        assertPolicy(cacheManager, CacheNames.TRENDING_POSTS_ANONYMOUS, 200, 30);
        assertPolicy(cacheManager, CacheNames.HOME_LANDING_ANONYMOUS, 16, 30);
    }

    @Test
    void readOptimizationIsDisabledByDefault() {
        assertThat(cacheConfig.cacheFeatureProperties().isReadOptimizationEnabled()).isFalse();
    }

    private void assertPolicy(CacheManager cacheManager, String cacheName, long maximumSize, long expirySeconds) {
        org.springframework.cache.Cache springCache = cacheManager.getCache(cacheName);
        assertThat(springCache).isNotNull();

        @SuppressWarnings("unchecked")
        Cache<Object, Object> nativeCache = (Cache<Object, Object>) springCache.getNativeCache();
        assertThat(nativeCache.policy().eviction()).hasValueSatisfying(
                eviction -> assertThat(eviction.getMaximum()).isEqualTo(maximumSize));
        assertThat(nativeCache.policy().expireAfterWrite()).hasValueSatisfying(
                expiry -> assertThat(expiry.getExpiresAfter(TimeUnit.SECONDS)).isEqualTo(expirySeconds));
        assertThat(nativeCache.stats().requestCount()).isZero();
    }
}
