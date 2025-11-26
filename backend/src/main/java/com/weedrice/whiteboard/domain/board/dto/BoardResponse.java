package com.weedrice.whiteboard.domain.board.dto;

import com.weedrice.whiteboard.domain.board.entity.Board;
import lombok.Getter;

@Getter
public class BoardResponse {
    private final Long boardId;
    private final String boardName;
    private final String description;
    private final String iconUrl;

    public BoardResponse(Board board) {
        this.boardId = board.getBoardId();
        this.boardName = board.getBoardName();
        this.description = board.getDescription();
        this.iconUrl = board.getIconUrl();
    }
}
