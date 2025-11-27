package com.weedrice.whiteboard.domain.board.dto;

import com.weedrice.whiteboard.domain.board.entity.Board;
import lombok.Getter;

@Getter
public class BoardResponse {
    private final Long boardId;
    private final String boardName;
    private final String description;
    private final String iconUrl;
    private final long subscriberCount;
    private final String adminDisplayName;

    public BoardResponse(Board board, long subscriberCount, String adminDisplayName) {
        this.boardId = board.getBoardId();
        this.boardName = board.getBoardName();
        this.description = board.getDescription();
        this.iconUrl = board.getIconUrl();
        this.subscriberCount = subscriberCount;
        this.adminDisplayName = adminDisplayName;
    }
}
