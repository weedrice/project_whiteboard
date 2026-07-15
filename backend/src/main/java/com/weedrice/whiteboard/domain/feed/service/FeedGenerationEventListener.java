package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.feed.event.PostPublishedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class FeedGenerationEventListener {

    private final FeedGenerationJobService feedGenerationJobService;

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enqueuePostPublished(PostPublishedEvent event) {
        try {
            feedGenerationJobService.enqueuePostPublishedJob(event.postId(), event.boardId());
        } catch (DataIntegrityViolationException ex) {
            if (feedGenerationJobService.existsPostPublishedJob(event.postId())) {
                log.warn("Skipped duplicate feed generation job enqueue. postId={}, boardId={}",
                        event.postId(), event.boardId(), ex);
                return;
            }
            log.error("Failed to enqueue feed generation job after post publish. postId={}, boardId={}",
                    event.postId(), event.boardId(), ex);
        } catch (Exception ex) {
            log.error("Failed to enqueue feed generation job after post publish. postId={}, boardId={}",
                    event.postId(), event.boardId(), ex);
        }
    }

    @Async("durableTaskExecutor")
    @Order(Ordered.LOWEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePostPublished(PostPublishedEvent event) {
        try {
            if (!feedGenerationJobService.existsPostPublishedJob(event.postId())) {
                log.warn("Skipped immediate feed generation because enqueue job does not exist. postId={}, boardId={}",
                        event.postId(), event.boardId());
                return;
            }
            feedGenerationJobService.processPostPublishedJob(event.postId());
        } catch (Exception ex) {
            log.error("Failed to process feed generation job after post publish. postId={}, boardId={}",
                    event.postId(), event.boardId(), ex);
        }
    }
}
