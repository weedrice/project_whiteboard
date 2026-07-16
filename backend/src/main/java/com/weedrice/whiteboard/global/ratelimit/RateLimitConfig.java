package com.weedrice.whiteboard.global.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

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
     * 사용자별 Rate Limit 생성 - rate-limit.user-limit 사용 (인증된 사용자)
     */
    public Bucket createUserBucket() {
        return createBucket(properties.getUserLimit());
    }

    /**
     * IP별 일반 API rate limit 버킷 생성.
     */
    public Bucket createApiBucket() {
        return createBucket(properties.getApiLimit());
    }

    /**
     * IP별 인증 API rate limit 버킷 생성.
     */
    public Bucket createAuthBucket() {
        return createBucket(properties.getAuthLimit());
    }

    public Bucket createAgentRegisterBucket() {
        return createBucket(properties.getAgentRegisterLimit());
    }

    /**
     * IP별 공개 로그 수집 API rate limit 버킷 생성.
     */
    public Bucket createPublicLogBucket() {
        return createBucket(properties.getPublicLogLimit());
    }

    public Bucket createAuthAccountBucket() {
        return createBucket(properties.getAuthAccountLimit());
    }

    public int getAuthLimit() {
        return properties.getAuthLimit();
    }

    public int getAgentRegisterLimit() {
        return properties.getAgentRegisterLimit();
    }

    public int getPublicLogLimit() {
        return properties.getPublicLogLimit();
    }

    public int getApiLimit() {
        return properties.getApiLimit();
    }

    public int getUserLimit() {
        return properties.getUserLimit();
    }

    private Bucket createBucket(int limit) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit)
                        .refillIntervally(limit, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
