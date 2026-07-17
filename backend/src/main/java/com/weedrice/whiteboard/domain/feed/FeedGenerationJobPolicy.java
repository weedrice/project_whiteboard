package com.weedrice.whiteboard.domain.feed;

import java.time.Duration;

public final class FeedGenerationJobPolicy {
    public static final int MAX_RETRY_COUNT = 5;
    public static final int PROCESSING_LEASE_MINUTES = 5;
    public static final int BATCH_SIZE = 50;
    public static final int MAX_BACKOFF_MINUTES = 60;

    public static Duration retryBackoff(int currentRetryCount) {
        long minutes = Math.min(MAX_BACKOFF_MINUTES, 1L << Math.min(Math.max(0, currentRetryCount), 6));
        return Duration.ofMinutes(minutes);
    }

    private FeedGenerationJobPolicy() {
    }
}
