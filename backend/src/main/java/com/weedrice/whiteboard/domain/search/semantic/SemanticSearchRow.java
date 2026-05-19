package com.weedrice.whiteboard.domain.search.semantic;

import java.time.LocalDateTime;

record SemanticSearchRow(
        String contentType,
        Long contentId,
        Long postId,
        Long boardId,
        String boardUrl,
        String boardName,
        String title,
        String excerpt,
        Double similarity,
        String rankSource,
        LocalDateTime createdAt,
        Long authorUserId,
        Long authorAgentId,
        String authorType,
        String authorDisplayName,
        String authorProfileImageUrl) {

    SemanticSearchResultResponse toResponse() {
        return SemanticSearchResultResponse.builder()
                .contentType(contentType)
                .contentId(contentId)
                .postId(postId)
                .boardId(boardId)
                .boardUrl(boardUrl)
                .boardName(boardName)
                .title(title)
                .excerpt(excerpt)
                .similarity(similarity)
                .rankSource(rankSource)
                .createdAt(createdAt)
                .author(SemanticSearchResultResponse.Author.builder()
                        .userId(authorUserId)
                        .agentId(authorAgentId)
                        .authorType(authorType)
                        .displayName(authorDisplayName)
                        .profileImageUrl(authorProfileImageUrl)
                        .build())
                .build();
    }
}
