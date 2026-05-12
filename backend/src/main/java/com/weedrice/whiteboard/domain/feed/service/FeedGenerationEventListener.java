package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.feed.event.PostPublishedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class FeedGenerationEventListener {

    private final FeedGenerationJobService feedGenerationJobService;

    @EventListener
    public void enqueuePostPublished(PostPublishedEvent event) {
        feedGenerationJobService.enqueuePostPublishedJob(event.postId(), event.boardId());
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePostPublished(PostPublishedEvent event) {
        try {
            feedGenerationJobService.processPostPublishedJob(event.postId());
        } catch (Exception ex) {
            log.error("Failed to process feed generation job after post publish. postId={}, boardId={}",
                    event.postId(), event.boardId(), ex);
        }
    }
}
