package com.weedrice.whiteboard.global.ratelimit;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;

public final class RateLimitHeaderWriter {

    public static final String HEADER_LIMIT = "RateLimit-Limit";
    public static final String HEADER_REMAINING = "RateLimit-Remaining";
    public static final String HEADER_RESET = "RateLimit-Reset";
    public static final String HEADER_RETRY_AFTER = "Retry-After";

    private RateLimitHeaderWriter() {
    }

    public static void write(HttpServletResponse response, RateLimitSnapshot snapshot) {
        response.setHeader(HEADER_LIMIT, Long.toString(snapshot.limit()));
        response.setHeader(HEADER_REMAINING, Long.toString(snapshot.remaining()));
        response.setHeader(HEADER_RESET, Long.toString(snapshot.resetSeconds()));
        if (snapshot.retryAfterSeconds() != null) {
            response.setHeader(HEADER_RETRY_AFTER, Long.toString(snapshot.retryAfterSeconds()));
        }
    }

    public static ResponseEntity.BodyBuilder apply(ResponseEntity.BodyBuilder builder, RateLimitSnapshot snapshot) {
        builder.header(HEADER_LIMIT, Long.toString(snapshot.limit()));
        builder.header(HEADER_REMAINING, Long.toString(snapshot.remaining()));
        builder.header(HEADER_RESET, Long.toString(snapshot.resetSeconds()));
        if (snapshot.retryAfterSeconds() != null) {
            builder.header(HEADER_RETRY_AFTER, Long.toString(snapshot.retryAfterSeconds()));
        }
        return builder;
    }

    public static long secondsUntilRefill(long nanosToWaitForRefill) {
        if (nanosToWaitForRefill <= 0) {
            return 0;
        }
        return Math.max(1, (long) Math.ceil(nanosToWaitForRefill / 1_000_000_000.0));
    }
}
