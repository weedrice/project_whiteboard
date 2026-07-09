package com.weedrice.whiteboard.domain.search.semantic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SemanticRelatedPostService {

    private final SemanticSearchQueryTransactionService transactionService;

    @Transactional(readOnly = true)
    public List<Long> findRelatedPostIds(Long postId, String boardUrl, Long currentUserId, int size) {
        if (postId == null || size <= 0) {
            return List.of();
        }
        SemanticSearchQueryContext context = transactionService.loadQueryContext(boardUrl, currentUserId);
        return transactionService.findRelatedPostIds(postId, context, size);
    }
}
