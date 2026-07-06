package com.weedrice.whiteboard.global.ratelimit;

public record RateLimitSnapshot(
        long limit,
        long remaining,
        long resetSeconds,
        Long retryAfterSeconds) {

    public static RateLimitSnapshot allowed(long limit, long remaining, long resetSeconds) {
        return new RateLimitSnapshot(limit, remaining, resetSeconds, null);
    }

    public static RateLimitSnapshot rejected(long limit, long remaining, long retryAfterSeconds) {
        return new RateLimitSnapshot(limit, remaining, retryAfterSeconds, retryAfterSeconds);
    }
}
