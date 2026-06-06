package com.weedrice.whiteboard.domain.search.semantic;

import java.util.List;

record SemanticSearchKeywordQuery(
        SemanticSearchContentType contentType,
        String keyword,
        String boardUrl,
        Long viewerUserId,
        boolean viewerSuperAdmin,
        List<Long> blockedUserIds,
        int limit,
        long offset) implements SemanticSearchSqlCriteria {
}
