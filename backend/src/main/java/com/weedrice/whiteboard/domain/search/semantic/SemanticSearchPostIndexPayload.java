package com.weedrice.whiteboard.domain.search.semantic;

record SemanticSearchPostIndexPayload(
        Long postId,
        Long boardId,
        Long authorUserId,
        Long authorAgentId,
        String embeddingText,
        String embeddingHash) {
}
