package com.weedrice.whiteboard.domain.mqueue;

public final class MessageQueuePolicy {
    public static final int MAX_RETRY_COUNT = 5;
    public static final int PROCESSING_LEASE_MINUTES = 5;

    private MessageQueuePolicy() {
    }
}
