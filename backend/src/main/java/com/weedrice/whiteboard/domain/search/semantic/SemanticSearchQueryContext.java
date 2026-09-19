package com.weedrice.whiteboard.domain.search.semantic;

import java.util.List;

record SemanticSearchQueryContext(
        String boardUrl,
        List<Long> blockedUserIds) {
}
