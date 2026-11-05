package com.weedrice.whiteboard.domain.search.semantic;

import java.util.List;

record SemanticSearchQuery(
        SemanticSearchContentType contentType,
        String boardUrl,
        List<Long> blockedUserIds,
        String embeddingVector,
        int limit,
        long offset,
        double minSimilarity,
        int candidateLimit) implements SemanticSearchSqlCriteria {
    SemanticSearchQuery(SemanticSearchContentType contentType, String boardUrl, List<Long> blockedUserIds,
                        String embeddingVector, int limit, long offset) {
        this(contentType, boardUrl, blockedUserIds, embeddingVector, limit, offset, 0.20, 200);
    }

    SemanticSearchQuery candidates() {
        return new SemanticSearchQuery(contentType, boardUrl, blockedUserIds, embeddingVector,
                candidateLimit, 0, minSimilarity, candidateLimit);
    }
}
