package com.weedrice.whiteboard.domain.search.semantic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
class SemanticSearchEventListener {

    private final SemanticSearchJobService jobService;

    @Order(Ordered.LOWEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enqueue(SemanticSearchIndexChangedEvent event) {
        try {
            jobService.enqueue(event.contentType(), event.contentId(), event.action());
        } catch (Exception ex) {
            log.error("Failed to enqueue semantic search job. contentType={}, contentId={}, action={}",
                    event.contentType(), event.contentId(), event.action(), ex);
        }
    }

    @Order(Ordered.LOWEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enqueuePostComments(SemanticSearchPostCommentsReindexEvent event) {
        try {
            jobService.enqueuePostComments(event.postId());
        } catch (Exception ex) {
            log.error("Failed to enqueue semantic search comment reindex jobs. postId={}", event.postId(), ex);
        }
    }

    @Order(Ordered.LOWEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enqueueBoardContent(SemanticSearchBoardContentReindexEvent event) {
        try {
            jobService.enqueueBoardContent(event.boardId());
        } catch (Exception ex) {
            log.error("Failed to enqueue semantic search board reindex jobs. boardId={}", event.boardId(), ex);
        }
    }
}
