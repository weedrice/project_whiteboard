package com.weedrice.whiteboard.domain.board.controller;

import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardUpdateRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
        return ApiResponse.success(boardService.getActiveBoards());
    }

    @GetMapping("/boards/top")
    public ApiResponse<List<BoardResponse>> getTopBoards() {
        return ApiResponse.success(boardService.getTopBoards());
    }

    @GetMapping("/boards/{boardId}")
    public ApiResponse<BoardResponse> getBoardDetails(@PathVariable Long boardId) {
        return ApiResponse.success(boardService.getBoardDetails(boardId));
    }

    @PostMapping("/boards")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BoardResponse> createBoard(@Valid @RequestBody BoardCreateRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Board board = boardService.createBoard(userDetails.getUserId(), request);
        return ApiResponse.success(boardService.getBoardDetails(board.getBoardId()));
    }

    @PutMapping("/boards/{boardId}")
    public ApiResponse<BoardResponse> updateBoard(@PathVariable Long boardId, @Valid @RequestBody BoardUpdateRequest request, @AuthenticationPrincipal UserDetails userDetails) {
        boardService.updateBoard(boardId, request, userDetails);
        return ApiResponse.success(boardService.getBoardDetails(boardId));
    }

    @DeleteMapping("/boards/{boardId}")
    public ApiResponse<Void> deleteBoard(@PathVariable Long boardId, @AuthenticationPrincipal UserDetails userDetails) {
        boardService.deleteBoard(boardId, userDetails);
        return ApiResponse.success(null);
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
    public ApiResponse<Void> subscribeBoard(@PathVariable Long boardId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        boardService.subscribeBoard(userDetails.getUserId(), boardId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/boards/{boardId}/subscribe")
    public ApiResponse<Void> unsubscribeBoard(@PathVariable Long boardId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        boardService.unsubscribeBoard(userDetails.getUserId(), boardId);
        return ApiResponse.success(null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/boards/{boardId}/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse> createCategory(@PathVariable Long boardId, @Valid @RequestBody CategoryRequest request) {
        BoardCategory category = boardService.createCategory(boardId, request);
        return ApiResponse.success(new CategoryResponse(category));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/boards/categories/{categoryId}")
    public ApiResponse<CategoryResponse> updateCategory(@PathVariable Long categoryId, @Valid @RequestBody CategoryRequest request) {
        BoardCategory category = boardService.updateCategory(categoryId, request);
        return ApiResponse.success(new CategoryResponse(category));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/boards/categories/{categoryId}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long categoryId) {
        boardService.deleteCategory(categoryId);
        return ApiResponse.success(null);
    }
}
