package com.weedrice.whiteboard.domain.feed;

public final class FeedGenerationJobPolicy {
    public static final int MAX_RETRY_COUNT = 5;
    public static final int PROCESSING_LEASE_MINUTES = 5;
    public static final int BATCH_SIZE = 50;

    private FeedGenerationJobPolicy() {
    }
}
