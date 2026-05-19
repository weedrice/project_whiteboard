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
        long offset) {

    boolean hasBoardUrl() {
        return boardUrl != null && !boardUrl.isBlank();
    }

    boolean hasBlockedUserIds() {
        return blockedUserIds != null && !blockedUserIds.isEmpty();
    }
}
