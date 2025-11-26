package com.weedrice.whiteboard.domain.board.controller;

import com.weedrice.whiteboard.domain.board.dto.BoardResponse;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/{boardId}/categories")
    public ApiResponse<List<CategoryResponse>> getCategories(@PathVariable Long boardId) {
        List<BoardCategory> categories = boardService.getActiveCategories(boardId);
        List<CategoryResponse> response = categories.stream()
                .map(CategoryResponse::new)
                .collect(Collectors.toList());
        return ApiResponse.success(response);
    }

    @PostMapping("/{boardId}/subscribe")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> subscribeBoard(@PathVariable Long boardId, Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        boardService.subscribeBoard(userId, boardId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{boardId}/subscribe")
    public ApiResponse<Void> unsubscribeBoard(@PathVariable Long boardId, Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        boardService.unsubscribeBoard(userId, boardId);
        return ApiResponse.success(null);
    }
}
