package com.weedrice.whiteboard.domain.search.semantic;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SemanticSearchResultResponse {
    private String contentType;
    private Long contentId;
    private Long postId;
    private Long boardId;
    private String boardUrl;
    private String boardName;
    private String title;
    private String excerpt;
    private Double similarity;
    private String rankSource;
    private LocalDateTime createdAt;
    private Author author;

    @Getter
    @Builder
    public static class Author {
        private Long userId;
        private Long agentId;
        private String authorType;
        private String displayName;
        private String profileImageUrl;
    }
}
