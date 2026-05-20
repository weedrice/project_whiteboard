package com.weedrice.whiteboard.domain.search.semantic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SemanticSearchIndexServiceTest {

    private EmbeddingClient embeddingClient;
    private SemanticSearchIndexTransactionService transactionService;
    private SemanticSearchIndexService indexService;

    @BeforeEach
    void setUp() {
        embeddingClient = mock(EmbeddingClient.class);
        transactionService = mock(SemanticSearchIndexTransactionService.class);
        indexService = new SemanticSearchIndexService(embeddingClient, transactionService);
    }

    @Test
    void upsertPost_embedsOutsidePayloadLookupAndWriteSteps() {
        SemanticSearchPostIndexPayload payload =
                new SemanticSearchPostIndexPayload(1L, 10L, 100L, null, "post text", "hash");
        float[] embedding = new float[] { 0.1F };
        when(transactionService.loadPostIndexPayload(1L)).thenReturn(payload);
        when(embeddingClient.embed("post text")).thenReturn(embedding);
        when(embeddingClient.model()).thenReturn("text-embedding");

        indexService.upsertPost(1L);

        var inOrder = inOrder(transactionService, embeddingClient);
        inOrder.verify(transactionService).loadPostIndexPayload(1L);
        inOrder.verify(embeddingClient).embed("post text");
        inOrder.verify(embeddingClient).model();
        inOrder.verify(transactionService).upsertPost(payload, "text-embedding", embedding);
    }

    @Test
    void upsertPost_tombstonesWhenPayloadMissing() {
        when(transactionService.loadPostIndexPayload(1L)).thenReturn(null);

        indexService.upsertPost(1L);

        verify(transactionService).tombstonePost(1L);
        verifyNoInteractions(embeddingClient);
    }

    @Test
    void upsertComment_embedsOutsidePayloadLookupAndWriteSteps() {
        SemanticSearchCommentIndexPayload payload =
                new SemanticSearchCommentIndexPayload(2L, 1L, 10L, 100L, null, "comment text", "hash");
        float[] embedding = new float[] { 0.2F };
        when(transactionService.loadCommentIndexPayload(2L)).thenReturn(payload);
        when(embeddingClient.embed("comment text")).thenReturn(embedding);
        when(embeddingClient.model()).thenReturn("text-embedding");

        indexService.upsertComment(2L);

        var inOrder = inOrder(transactionService, embeddingClient);
        inOrder.verify(transactionService).loadCommentIndexPayload(2L);
        inOrder.verify(embeddingClient).embed("comment text");
        inOrder.verify(embeddingClient).model();
        inOrder.verify(transactionService).upsertComment(payload, "text-embedding", embedding);
    }

    @Test
    void upsertComment_tombstonesWhenPayloadMissing() {
        when(transactionService.loadCommentIndexPayload(2L)).thenReturn(null);

        indexService.upsertComment(2L);

        verify(transactionService).tombstoneComment(2L);
        verifyNoInteractions(embeddingClient);
    }

    @Test
    void serviceMethodsAreNotTransactional() throws NoSuchMethodException {
        assertThat(SemanticSearchIndexService.class.getAnnotation(Transactional.class)).isNull();
        assertThat(transactionalAnnotation("upsertPost", Long.class)).isNull();
        assertThat(transactionalAnnotation("upsertComment", Long.class)).isNull();
        assertThat(transactionalAnnotation("delete", String.class, Long.class)).isNull();
    }

    private Transactional transactionalAnnotation(String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = SemanticSearchIndexService.class.getMethod(methodName, parameterTypes);
        return method.getAnnotation(Transactional.class);
    }
}
