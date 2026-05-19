package com.weedrice.whiteboard.domain.search.semantic;

public record SemanticSearchIndexChangedEvent(
        String contentType,
        Long contentId,
        SemanticSearchIndexAction action) {
}
