package com.weedrice.whiteboard.domain.board.dto;

import com.weedrice.whiteboard.domain.board.entity.Board;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BoardSummary {
    private Long boardId;
    private String boardName;
    private String boardUrl;
    private String description;
    private String iconUrl;

    public static BoardSummary from(Board board) {
        return BoardSummary.builder()
                .boardId(board.getBoardId())
                .boardName(board.getBoardName())
                .boardUrl(board.getBoardUrl())
                .description(board.getDescription())
                .iconUrl(board.getIconUrl())
                .build();
    }
}
