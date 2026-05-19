package com.weedrice.whiteboard.domain.search.semantic;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SemanticSearchEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publish(String contentType, Long contentId, SemanticSearchIndexAction action) {
        eventPublisher.publishEvent(new SemanticSearchIndexChangedEvent(contentType, contentId, action));
    }

    public void publishPostCommentsReindex(Long postId) {
        eventPublisher.publishEvent(new SemanticSearchPostCommentsReindexEvent(postId));
    }

    public void publishBoardContentReindex(Long boardId) {
        eventPublisher.publishEvent(new SemanticSearchBoardContentReindexEvent(boardId));
    }
}
