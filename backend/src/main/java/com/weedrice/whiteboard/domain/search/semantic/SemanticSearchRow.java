package com.weedrice.whiteboard.domain.search.semantic;

import java.time.LocalDateTime;
import com.weedrice.whiteboard.domain.actor.AuthorPresentation;

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
        String authorProfileImageUrl,
        boolean authorWithdrawn) {

    public SemanticSearchRow(
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
        this(contentType, contentId, postId, boardId, boardUrl, boardName, title, excerpt, similarity, rankSource, createdAt, authorUserId, authorAgentId, authorType, authorDisplayName, authorProfileImageUrl, false);
    }

    public SemanticSearchRow {
        authorDisplayName = AuthorPresentation.displayName(authorWithdrawn, authorDisplayName);
        authorProfileImageUrl = AuthorPresentation.profileImageUrl(authorWithdrawn, authorProfileImageUrl);
    }

    SemanticSearchResultResponse toResponse() {
        return toResponse(excerpt);
    }

    SemanticSearchResultResponse toResponse(String excerpt) {
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
                        .withdrawn(authorWithdrawn)
                        .profileImageUrl(authorProfileImageUrl)
                        .build())
                .build();
    }
}
