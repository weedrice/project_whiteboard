package com.weedrice.whiteboard.domain.search.semantic;

import java.time.Duration;

/** Safe provider failure metadata; never retains response bodies or credentials. */
final class EmbeddingFailureException extends RuntimeException {
    private final boolean retryable;
    private final Duration retryAfter;
    private final String code;

    EmbeddingFailureException(String code, boolean retryable, Duration retryAfter) {
        super(code);
        this.code = code;
        this.retryable = retryable;
        this.retryAfter = retryAfter;
    }

    boolean retryable() { return retryable; }
    Duration retryAfter() { return retryAfter; }
    String code() { return code; }
}
