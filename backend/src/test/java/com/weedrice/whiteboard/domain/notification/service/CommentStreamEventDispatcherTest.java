package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.CommentStreamEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CommentStreamEventDispatcherTest {

    private final CommentStreamPublisher publisher = mock(CommentStreamPublisher.class);
    private final CommentStreamEventDispatcher dispatcher = new CommentStreamEventDispatcher(publisher);
    private final CommentStreamEvent event = CommentStreamEvent.builder()
            .action("CREATED")
            .postId(1L)
            .commentId(2L)
            .actorUserId(3L)
            .build();

    @AfterEach
    void cleanUpTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishesOnlyAfterCommitWhenTransactionIsActive() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        dispatcher.publishAfterCommit(event);

        verify(publisher, never()).publishCommentEvent(event);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(publisher).publishCommentEvent(event);
    }

    @Test
    void doesNotPublishWhenTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        dispatcher.publishAfterCommit(event);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(publisher, never()).publishCommentEvent(event);
    }

    @Test
    void isolatesPublisherFailureOutsideTransaction() {
        doThrow(new IllegalStateException("stream failed"))
                .when(publisher).publishCommentEvent(event);

        dispatcher.publishAfterCommit(event);

        verify(publisher).publishCommentEvent(event);
    }
}
