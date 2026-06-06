package com.weedrice.whiteboard.domain.search.semantic;

import java.util.List;

interface SemanticSearchSqlCriteria {

    SemanticSearchContentType contentType();

    String boardUrl();

    Long viewerUserId();

    boolean viewerSuperAdmin();

    List<Long> blockedUserIds();

    int limit();

    long offset();

    default boolean hasBoardUrl() {
        return boardUrl() != null && !boardUrl().isBlank();
    }

    default boolean hasBlockedUserIds() {
        return blockedUserIds() != null && !blockedUserIds().isEmpty();
    }
}
