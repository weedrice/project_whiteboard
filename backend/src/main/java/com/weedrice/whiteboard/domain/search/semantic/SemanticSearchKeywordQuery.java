package com.weedrice.whiteboard.domain.search.semantic;

import java.util.List;

record SemanticSearchKeywordQuery(
        SemanticSearchContentType contentType,
        String keyword,
        String boardUrl,
        List<Long> blockedUserIds,
        int limit,
        long offset,
        int candidateLimit) implements SemanticSearchSqlCriteria {
    SemanticSearchKeywordQuery(SemanticSearchContentType contentType, String keyword, String boardUrl,
                               List<Long> blockedUserIds, int limit, long offset) {
        this(contentType, keyword, boardUrl, blockedUserIds, limit, offset, 200);
    }

    SemanticSearchKeywordQuery candidates() {
        return new SemanticSearchKeywordQuery(contentType, keyword, boardUrl, blockedUserIds,
                candidateLimit, 0, candidateLimit);
    }
}
