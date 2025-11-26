package com.weedrice.whiteboard.domain.board.controller;

import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardUpdateRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @GetMapping("/boards")
    public ApiResponse<List<BoardResponse>> getBoards() {
        List<Board> boards = boardService.getActiveBoards();
        List<BoardResponse> response = boards.stream()
                .map(BoardResponse::new)
                .collect(Collectors.toList());
        return ApiResponse.success(response);
    }

    @GetMapping("/boards/{boardId}/categories")
    public ApiResponse<List<CategoryResponse>> getCategories(@PathVariable Long boardId) {
        List<BoardCategory> categories = boardService.getActiveCategories(boardId);
        List<CategoryResponse> response = categories.stream()
                .map(CategoryResponse::new)
                .collect(Collectors.toList());
        return ApiResponse.success(response);
    }

    @PostMapping("/boards/{boardId}/subscribe")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> subscribeBoard(@PathVariable Long boardId, Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        boardService.subscribeBoard(userId, boardId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/boards/{boardId}/subscribe")
    public ApiResponse<Void> unsubscribeBoard(@PathVariable Long boardId, Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        boardService.unsubscribeBoard(userId, boardId);
        return ApiResponse.success(null);
    }

    @GetMapping("/users/me/subscriptions")
    public ApiResponse<Page<BoardResponse>> getMySubscriptions(Authentication authentication, Pageable pageable) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        return ApiResponse.success(boardService.getMySubscriptions(userId, pageable).map(sub -> new BoardResponse(sub.getBoard())));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/boards")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BoardResponse> createBoard(@Valid @RequestBody BoardCreateRequest request, Authentication authentication) {
        Long creatorId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        Board board = boardService.createBoard(creatorId, request);
        return ApiResponse.success(new BoardResponse(board));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/boards/{boardId}")
    public ApiResponse<BoardResponse> updateBoard(@PathVariable Long boardId, @Valid @RequestBody BoardUpdateRequest request) {
        Board board = boardService.updateBoard(boardId, request);
        return ApiResponse.success(new BoardResponse(board));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/boards/{boardId}")
    public ApiResponse<Void> deleteBoard(@PathVariable Long boardId) {
        boardService.deleteBoard(boardId);
        return ApiResponse.success(null);
    }
}
