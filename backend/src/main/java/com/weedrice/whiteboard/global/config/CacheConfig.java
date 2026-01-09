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

    /**
     * Caffeine CacheManager 설정
     * 
     * - globalConfig: 전역 설정 캐시 (10분 TTL, 최대 1000개 항목)
     * - 기타 캐시는 필요시 추가 가능
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // globalConfig 캐시 설정
        @SuppressWarnings("null")
        Cache<Object, Object> globalConfigCache = Caffeine.newBuilder()
                .maximumSize(1000)                    // 최대 1000개 항목
                .expireAfterWrite(10, TimeUnit.MINUTES) // 10분 후 만료
                .recordStats()                        // 통계 수집 (모니터링용)
                .build();
        cacheManager.registerCustomCache("globalConfig", globalConfigCache);
        
        // 필요시 다른 캐시도 여기에 추가 가능
        // 예: 사용자 정보, 게시판 목록 등
        
        return cacheManager;
    }
}
