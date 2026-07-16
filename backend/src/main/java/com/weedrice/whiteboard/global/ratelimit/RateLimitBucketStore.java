package com.weedrice.whiteboard.global.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class RateLimitBucketStore {

    private final Cache<String, Bucket> ipBuckets;
    private final Cache<String, Bucket> userBuckets;

    public RateLimitBucketStore(RateLimitProperties properties, MeterRegistry meterRegistry) {
        this.ipBuckets = createCache(properties);
        this.userBuckets = createCache(properties);
        bindMetrics(meterRegistry, ipBuckets, "ip");
        bindMetrics(meterRegistry, userBuckets, "user");
    }

    public Bucket getIpBucket(String key, Supplier<Bucket> factory) {
        return ipBuckets.get(key, ignored -> factory.get());
    }

    public Bucket getUserBucket(String key, Supplier<Bucket> factory) {
        return userBuckets.get(key, ignored -> factory.get());
    }

    Cache<String, Bucket> ipBuckets() {
        return ipBuckets;
    }

    Cache<String, Bucket> userBuckets() {
        return userBuckets;
    }

    private Cache<String, Bucket> createCache(RateLimitProperties properties) {
        return Caffeine.newBuilder()
                .maximumSize(properties.getBucketCacheMaxSize())
                .expireAfterAccess(Duration.ofMinutes(properties.getBucketCacheTtlMinutes()))
                .recordStats()
                .build();
    }

    private void bindMetrics(MeterRegistry meterRegistry, Cache<String, Bucket> cache, String scope) {
        Gauge.builder("noviis.ratelimit.cache.size", cache, value -> value.estimatedSize())
                .tag("scope", scope)
                .register(meterRegistry);
        FunctionCounter.builder(
                        "noviis.ratelimit.cache.evictions",
                        cache,
                        value -> value.stats().evictionCount())
                .tag("scope", scope)
                .register(meterRegistry);
    }
}
