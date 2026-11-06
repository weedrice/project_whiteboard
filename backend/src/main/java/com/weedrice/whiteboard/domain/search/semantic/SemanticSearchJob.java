package com.weedrice.whiteboard.domain.search.semantic;

import java.time.LocalDateTime;

record SemanticSearchJob(
        Long jobId,
        String contentType,
        Long contentId,
        String action,
        LocalDateTime processingStartedAt,
        int retryCount) {
    SemanticSearchJob(Long jobId, String contentType, Long contentId, String action,
                      LocalDateTime processingStartedAt) {
        this(jobId, contentType, contentId, action, processingStartedAt, 0);
    }
}
