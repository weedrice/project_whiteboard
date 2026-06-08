package com.weedrice.whiteboard.domain.search.semantic;

import java.util.List;

record SemanticSearchQuery(
        SemanticSearchContentType contentType,
        String boardUrl,
        Long viewerUserId,
        boolean viewerSuperAdmin,
        List<Long> blockedUserIds,
        String embeddingVector,
        int limit,
        long offset) implements SemanticSearchSqlCriteria {
}
