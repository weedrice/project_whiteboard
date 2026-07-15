package com.weedrice.whiteboard.domain.search.semantic;

import java.util.List;

public record SemanticRelatedPostResult(
        boolean sourceEmbeddingAvailable,
        List<Long> postIds) {

    public SemanticRelatedPostResult {
        postIds = postIds == null ? List.of() : List.copyOf(postIds);
    }

    static SemanticRelatedPostResult sourceEmbeddingUnavailable() {
        return new SemanticRelatedPostResult(false, List.of());
    }
}
