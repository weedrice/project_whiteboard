package com.weedrice.whiteboard.domain.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.weedrice.whiteboard.global.ratelimit.RateLimitConfig;
import com.weedrice.whiteboard.global.ratelimit.RateLimitExceededException;
import com.weedrice.whiteboard.global.ratelimit.RateLimitHeaderWriter;
import com.weedrice.whiteboard.global.ratelimit.RateLimitProperties;
import com.weedrice.whiteboard.global.ratelimit.RateLimitSnapshot;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

@Component
public class LoginAccountRateLimiter {

    private final RateLimitConfig rateLimitConfig;
    private final RateLimitProperties properties;
    private final Cache<String, Bucket> accountBucketCache;

    public LoginAccountRateLimiter(RateLimitConfig rateLimitConfig, RateLimitProperties properties) {
        this.rateLimitConfig = rateLimitConfig;
        this.properties = properties;
        this.accountBucketCache = Caffeine.newBuilder()
                .maximumSize(properties.getBucketCacheMaxSize())
                .expireAfterAccess(Duration.ofMinutes(properties.getBucketCacheTtlMinutes()))
                .<String, Bucket>build();
    }

    public void assertAllowed(String loginId) {
        Bucket bucket = resolveBucket(loginId);
        EstimationProbe probe = bucket.estimateAbilityToConsume(1);
        if (!probe.canBeConsumed()) {
            throw rateLimitExceeded(probe.getRemainingTokens(), probe.getNanosToWaitForRefill());
        }
    }

    public void recordFailure(String loginId) {
        Bucket bucket = resolveBucket(loginId);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw rateLimitExceeded(probe.getRemainingTokens(), probe.getNanosToWaitForRefill());
        }
    }

    public void reset(String loginId) {
        resolveBucket(loginId).reset();
    }

    private RateLimitExceededException rateLimitExceeded(long remainingTokens, long nanosToWaitForRefill) {
        return new RateLimitExceededException(RateLimitSnapshot.rejected(
                properties.getAuthAccountLimit(),
                Math.max(0, remainingTokens),
                RateLimitHeaderWriter.secondsUntilRefill(nanosToWaitForRefill)));
    }

    private Bucket resolveBucket(String loginId) {
        String key = LoginClientMetadataNormalizer.normalizeLoginId(loginId).toLowerCase(Locale.ROOT);
        return accountBucketCache.asMap().computeIfAbsent(key, ignored -> rateLimitConfig.createAuthAccountBucket());
    }
}
