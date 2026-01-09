package com.weedrice.whiteboard.global.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiting 설정
 * 
 * Bucket4j를 사용하여 API 요청 제한을 구현합니다.
 * 엔드포인트별, 사용자별로 다른 제한을 설정할 수 있습니다.
 */
@Configuration
public class RateLimitConfig {

    /**
     * 기본 Rate Limit (IP 기반)
     * - 100 requests per minute
     */
    @Bean
    public Bucket defaultBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1))))
                .build();
    }

    /**
     * 인증 엔드포인트용 Rate Limit (더 엄격)
     * - 5 requests per minute (무차별 대입 공격 방지)
     */
    @Bean(name = "authBucket")
    public Bucket authBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1))))
                .build();
    }

    /**
     * 일반 API 엔드포인트용 Rate Limit
     * - 200 requests per minute
     */
    @Bean(name = "apiBucket")
    public Bucket apiBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(200, Refill.intervally(200, Duration.ofMinutes(1))))
                .build();
    }

    /**
     * 사용자별 Rate Limit 저장소
     * 인증된 사용자는 더 높은 제한을 받을 수 있습니다.
     */
    @Bean
    public Map<String, Bucket> userBuckets() {
        return new ConcurrentHashMap<>();
    }

    /**
     * 사용자별 Rate Limit 생성
     * - 인증된 사용자: 500 requests per minute
     */
    public Bucket createUserBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(500, Refill.intervally(500, Duration.ofMinutes(1))))
                .build();
    }
}
