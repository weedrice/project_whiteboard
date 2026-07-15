package com.weedrice.whiteboard.domain.search.semantic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SemanticRelatedPostService {

    private final SemanticSearchQueryTransactionService transactionService;
    private final SemanticSearchProperties properties;

    @Transactional(readOnly = true)
    public SemanticRelatedPostResult findRelatedPostIds(Long postId, String boardUrl, Long currentUserId, int size) {
        if (postId == null || size <= 0) {
            return SemanticRelatedPostResult.sourceEmbeddingUnavailable();
        }
        SemanticSearchQueryContext context = transactionService.loadQueryContext(boardUrl, currentUserId);
        return transactionService.findRelatedPostIds(
                postId,
                context,
                size,
                properties.getRelatedPost().getMinSimilarity());
    }
}
