package com.weedrice.whiteboard.domain.search.semantic;

import java.util.List;

record SemanticSearchQuery(
        SemanticSearchContentType contentType,
        String boardUrl,
        List<Long> blockedUserIds,
        String embeddingVector,
        int limit,
        long offset) implements SemanticSearchSqlCriteria {
}
