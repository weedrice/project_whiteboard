package com.weedrice.whiteboard.global.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 캐시 설정
 * 
 * 각 캐시별로 적절한 TTL과 크기를 설정하여 메모리 사용을 최적화합니다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheFeatureProperties cacheFeatureProperties() {
        return new CacheFeatureProperties();
    }

    /**
     * Caffeine CacheManager 설정
     * 
     * - globalConfig: 전역 설정 캐시 (10분 TTL, 최대 1000개 항목)
     * - 기타 캐시는 필요시 추가 가능
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        register(cacheManager, CacheNames.GLOBAL_CONFIG, 1000, 10, TimeUnit.MINUTES);
        register(cacheManager, CacheNames.BOARD_CATALOG_ANONYMOUS, 32, 60, TimeUnit.SECONDS);
        register(cacheManager, CacheNames.BOARD_DETAIL_ANONYMOUS, 500, 30, TimeUnit.SECONDS);
        register(cacheManager, CacheNames.TRENDING_POSTS_ANONYMOUS, 200, 30, TimeUnit.SECONDS);
        register(cacheManager, CacheNames.HOME_LANDING_ANONYMOUS, 16, 30, TimeUnit.SECONDS);
        return cacheManager;
    }

    @SuppressWarnings("null")
    private void register(
            CaffeineCacheManager cacheManager,
            String cacheName,
            long maximumSize,
            long duration,
            TimeUnit timeUnit) {
        Cache<Object, Object> cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(duration, timeUnit)
                .build();
        cacheManager.registerCustomCache(cacheName, cache);
    }
}
