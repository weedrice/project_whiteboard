package com.weedrice.whiteboard.domain.board.dto;

import java.time.LocalDateTime;

public record BoardActivitySummary(
        Long boardId,
        LocalDateTime latestPostAt,
        long newPostCount
) {
    public boolean hasNewPosts() {
        return newPostCount > 0;
    }

    public static BoardActivitySummary empty(Long boardId) {
        return new BoardActivitySummary(boardId, null, 0L);
    }
}
