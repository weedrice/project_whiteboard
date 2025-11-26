package com.weedrice.whiteboard.domain.board.controller;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @GetMapping
    public ApiResponse<List<BoardResponse>> getBoards() {
        List<Board> boards = boardService.getActiveBoards();
        List<BoardResponse> response = boards.stream()
                .map(BoardResponse::new)
                .collect(Collectors.toList());
        return ApiResponse.success(response);
    }

    @lombok.Getter
    private static class BoardResponse {
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
}
