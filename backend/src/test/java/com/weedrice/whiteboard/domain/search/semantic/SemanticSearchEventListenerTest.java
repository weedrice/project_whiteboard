package com.weedrice.whiteboard.domain.search.semantic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SemanticSearchEventListenerTest {

    @Mock SemanticSearchJobService jobService;
    @InjectMocks SemanticSearchEventListener listener;

    @Test
    void singleContentEnqueueRunsBeforeCommitAndPropagatesFailure() throws Exception {
        SemanticSearchIndexChangedEvent event = new SemanticSearchIndexChangedEvent(
                "POST", 1L, SemanticSearchIndexAction.UPSERT);
        doThrow(new IllegalStateException("enqueue failed"))
                .when(jobService).enqueue("POST", 1L, SemanticSearchIndexAction.UPSERT);

        assertThatThrownBy(() -> listener.enqueue(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("enqueue failed");

        Method method = SemanticSearchEventListener.class
                .getMethod("enqueue", SemanticSearchIndexChangedEvent.class);
        assertThat(method.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.BEFORE_COMMIT);
    }

    @Test
    void bulkReindexEnqueuesUseCursorBasedServiceBeforeCommit() throws Exception {
        listener.enqueuePostComments(new SemanticSearchPostCommentsReindexEvent(2L));
        listener.enqueueBoardContent(new SemanticSearchBoardContentReindexEvent(3L));

        verify(jobService).enqueuePostComments(2L);
        verify(jobService).enqueueBoardContent(3L);
        assertBeforeCommit("enqueuePostComments", SemanticSearchPostCommentsReindexEvent.class);
        assertBeforeCommit("enqueueBoardContent", SemanticSearchBoardContentReindexEvent.class);
    }

    private void assertBeforeCommit(String methodName, Class<?> eventType) throws Exception {
        Method method = SemanticSearchEventListener.class.getMethod(methodName, eventType);
        assertThat(method.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.BEFORE_COMMIT);
    }
}
