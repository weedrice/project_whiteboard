package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.CommentStreamEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommentStreamEventDispatcher {

    private final CommentStreamPublisher commentStreamPublisher;

    public void publishAfterCommit(CommentStreamEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishSafely(event);
                }
            });
            return;
        }
        publishSafely(event);
    }

    private void publishSafely(CommentStreamEvent event) {
        try {
            commentStreamPublisher.publishCommentEvent(event);
        } catch (RuntimeException exception) {
            log.warn("Failed to publish comment stream event after commit: action={}, postId={}, commentId={}",
                    event.getAction(),
                    event.getPostId(),
                    event.getCommentId(),
                    exception);
        }
    }
}
