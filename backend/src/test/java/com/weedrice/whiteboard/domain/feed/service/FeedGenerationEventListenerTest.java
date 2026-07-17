package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.feed.event.PostPublishedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedGenerationEventListenerTest {

    @InjectMocks
    private FeedGenerationEventListener feedGenerationEventListener;

    @Mock
    private FeedGenerationJobService feedGenerationJobService;

    @Test
    @DisplayName("Post publish event enqueues feed generation job before commit")
    void enqueuePostPublished_savesFeedGenerationJob() {
        PostPublishedEvent event = new PostPublishedEvent(100L, 10L);

        feedGenerationEventListener.enqueuePostPublished(event);

        verify(feedGenerationJobService).enqueuePostPublishedJob(100L, 10L);
    }

    @Test
    @DisplayName("Feed job enqueue failure aborts the publishing transaction")
    void enqueuePostPublished_propagatesEnqueueFailure() {
        PostPublishedEvent event = new PostPublishedEvent(100L, 10L);
        doThrow(new DataIntegrityViolationException("duplicate feed generation job"))
                .when(feedGenerationJobService)
                .enqueuePostPublishedJob(100L, 10L);

        assertThatThrownBy(() -> feedGenerationEventListener.enqueuePostPublished(event))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(feedGenerationJobService).enqueuePostPublishedJob(100L, 10L);
    }

    @Test
    void enqueueListenerRunsBeforeCommit() throws Exception {
        Method method = FeedGenerationEventListener.class
                .getMethod("enqueuePostPublished", PostPublishedEvent.class);

        assertThat(method.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.BEFORE_COMMIT);
    }

    @Test
    @DisplayName("Post publish event processes feed generation job after commit")
    void handlePostPublished_processesFeedGenerationJob() {
        PostPublishedEvent event = new PostPublishedEvent(100L, 10L);
        when(feedGenerationJobService.existsPostPublishedJob(100L)).thenReturn(true);

        feedGenerationEventListener.handlePostPublished(event);

        verify(feedGenerationJobService).existsPostPublishedJob(100L);
        verify(feedGenerationJobService).processPostPublishedJob(100L);
    }

    @Test
    @DisplayName("Post publish event skips immediate processing without enqueue job")
    void handlePostPublished_skipsProcessingWithoutEnqueueJob() {
        PostPublishedEvent event = new PostPublishedEvent(100L, 10L);
        when(feedGenerationJobService.existsPostPublishedJob(100L)).thenReturn(false);

        feedGenerationEventListener.handlePostPublished(event);

        verify(feedGenerationJobService).existsPostPublishedJob(100L);
        verify(feedGenerationJobService, never()).processPostPublishedJob(100L);
    }

    @Test
    @DisplayName("Feed generation processing failure is swallowed")
    void handlePostPublished_swallowsGenerationFailure() {
        PostPublishedEvent event = new PostPublishedEvent(100L, 10L);
        when(feedGenerationJobService.existsPostPublishedJob(100L)).thenReturn(true);
        doThrow(new IllegalStateException("feed failure"))
                .when(feedGenerationJobService)
                .processPostPublishedJob(100L);

        feedGenerationEventListener.handlePostPublished(event);

        verify(feedGenerationJobService).existsPostPublishedJob(100L);
        verify(feedGenerationJobService).processPostPublishedJob(100L);
    }
}
