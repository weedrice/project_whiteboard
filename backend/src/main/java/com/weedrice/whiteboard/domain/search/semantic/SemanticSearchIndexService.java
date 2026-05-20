package com.weedrice.whiteboard.domain.search.semantic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class SemanticSearchIndexService {

    private final EmbeddingClient embeddingClient;
    private final SemanticSearchIndexTransactionService transactionService;

    public void upsertPost(Long postId) {
        SemanticSearchPostIndexPayload payload = transactionService.loadPostIndexPayload(postId);
        if (payload == null) {
            transactionService.tombstonePost(postId);
            return;
        }
        float[] embedding = embeddingClient.embed(payload.embeddingText());
        transactionService.upsertPost(payload, embeddingClient.model(), embedding);
    }

    public void upsertComment(Long commentId) {
        SemanticSearchCommentIndexPayload payload = transactionService.loadCommentIndexPayload(commentId);
        if (payload == null) {
            transactionService.tombstoneComment(commentId);
            return;
        }
        float[] embedding = embeddingClient.embed(payload.embeddingText());
        transactionService.upsertComment(payload, embeddingClient.model(), embedding);
    }

    public void delete(String contentType, Long contentId) {
        transactionService.delete(contentType, contentId);
    }
}
