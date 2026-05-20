package com.weedrice.whiteboard.domain.search.semantic;

record SemanticSearchCommentIndexPayload(
        Long commentId,
        Long postId,
        Long boardId,
        Long authorUserId,
        Long authorAgentId,
        String embeddingText,
        String embeddingHash) {
}
