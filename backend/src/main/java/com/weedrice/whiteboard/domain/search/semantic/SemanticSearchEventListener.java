package com.weedrice.whiteboard.domain.search.semantic;

import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
class SemanticSearchEventListener {

    private final SemanticSearchJobService jobService;

    @Order(Ordered.LOWEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void enqueue(SemanticSearchIndexChangedEvent event) {
        jobService.enqueue(event.contentType(), event.contentId(), event.action());
    }

    @Order(Ordered.LOWEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void enqueuePostComments(SemanticSearchPostCommentsReindexEvent event) {
        jobService.enqueuePostComments(event.postId());
    }

    @Order(Ordered.LOWEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void enqueueBoardContent(SemanticSearchBoardContentReindexEvent event) {
        jobService.enqueueBoardContent(event.boardId());
    }
}
