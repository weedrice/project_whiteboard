package com.weedrice.whiteboard.global.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

/**
 * Rate Limiting 설정
 *
 * Bucket4j를 사용하여 API 요청 제한을 구현합니다.
 * 엔드포인트별, 사용자별로 다른 제한은 application.yml의 rate-limit.* 로 설정합니다.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    private final RateLimitProperties properties;

    public RateLimitConfig(RateLimitProperties properties) {
        this.properties = properties;
    }

    /**
     * 기본 Rate Limit (IP 기반) - rate-limit.default-limit 사용
     */
    @Bean
    public Bucket defaultBucket() {
        int limit = properties.getDefaultLimit();
        return Bucket.builder()
                .addLimit(Bandwidth.classic(limit, Refill.intervally(limit, Duration.ofMinutes(1))))
                .build();
    }

    /**
     * 인증 엔드포인트용 Rate Limit - rate-limit.auth-limit 사용 (무차별 대입 방지)
     */
    @Bean(name = "authBucket")
    public Bucket authBucket() {
        int limit = properties.getAuthLimit();
        return Bucket.builder()
                .addLimit(Bandwidth.classic(limit, Refill.intervally(limit, Duration.ofMinutes(1))))
                .build();
    }

    /**
     * 일반 API 엔드포인트용 Rate Limit - rate-limit.api-limit 사용
     */
    @Bean(name = "apiBucket")
    public Bucket apiBucket() {
        int limit = properties.getApiLimit();
        return Bucket.builder()
                .addLimit(Bandwidth.classic(limit, Refill.intervally(limit, Duration.ofMinutes(1))))
                .build();
    }

    /**
     * 사용자별 Rate Limit 저장소
     */
    @Bean
    public Map<String, Bucket> userBuckets() {
        return Caffeine.newBuilder()
                .maximumSize(properties.getBucketCacheMaxSize())
                .expireAfterAccess(Duration.ofMinutes(properties.getBucketCacheTtlMinutes()))
                .<String, Bucket>build()
                .asMap();
    }

    /**
     * 사용자별 Rate Limit 생성 - rate-limit.user-limit 사용 (인증된 사용자)
     */
    public Bucket createUserBucket() {
        int limit = properties.getUserLimit();
        return Bucket.builder()
                .addLimit(Bandwidth.classic(limit, Refill.intervally(limit, Duration.ofMinutes(1))))
                .build();
    }

    /**
     * IP별 일반 API rate limit 버킷 생성.
     */
    public Bucket createApiBucket() {
        int limit = properties.getApiLimit();
        return Bucket.builder()
                .addLimit(Bandwidth.classic(limit, Refill.intervally(limit, Duration.ofMinutes(1))))
                .build();
    }

    /**
     * IP별 인증 API rate limit 버킷 생성.
     */
    public Bucket createAuthBucket() {
        int limit = properties.getAuthLimit();
        return Bucket.builder()
                .addLimit(Bandwidth.classic(limit, Refill.intervally(limit, Duration.ofMinutes(1))))
                .build();
    }
}
