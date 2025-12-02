package com.weedrice.whiteboard.domain.board.controller;

import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardUpdateRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;
    private final PostService postService; // PostService 주입

    @GetMapping
    public ApiResponse<List<BoardResponse>> getBoards(@AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(boardService.getActiveBoards(userDetails));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<BoardResponse>> getAllBoards(@AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(boardService.getAllBoards(userDetails));
    }

    @GetMapping("/top")
    public ApiResponse<List<BoardResponse>> getTopBoards(@AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(boardService.getTopBoards(userDetails));
    }

    @GetMapping("/{boardUrl}")
    public ApiResponse<BoardResponse> getBoardDetails(@PathVariable String boardUrl,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(boardService.getBoardDetails(boardUrl, userDetails));
    }

    @GetMapping("/{boardUrl}/notices")
    public ApiResponse<List<PostSummary>> getNotices(@PathVariable String boardUrl) {
        List<Post> notices = postService.getNotices(boardUrl);
        List<PostSummary> response = notices.stream().map(PostSummary::from).collect(Collectors.toList());
        return ApiResponse.success(response);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BoardResponse> createBoard(@Valid @RequestBody BoardCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Board board = boardService.createBoard(userDetails.getUserId(), request);
        return ApiResponse.success(boardService.getBoardDetails(board.getBoardUrl(), userDetails));
    }

    @PutMapping("/{boardUrl}")
    public ApiResponse<BoardResponse> updateBoard(@PathVariable String boardUrl,
            @Valid @RequestBody BoardUpdateRequest request, @AuthenticationPrincipal UserDetails userDetails) {
        boardService.updateBoard(boardUrl, request, userDetails);
        return ApiResponse.success(boardService.getBoardDetails(boardUrl, userDetails));
    }

    @DeleteMapping("/{boardUrl}")
    public ApiResponse<Void> deleteBoard(@PathVariable String boardUrl,
            @AuthenticationPrincipal UserDetails userDetails) {
        boardService.deleteBoard(boardUrl, userDetails);
        return ApiResponse.success(null);
    }

    @GetMapping("/{boardUrl}/categories")
    public ApiResponse<List<CategoryResponse>> getCategories(@PathVariable String boardUrl) {
        List<BoardCategory> categories = boardService.getActiveCategories(boardUrl);
        List<CategoryResponse> response = categories.stream()
                .map(CategoryResponse::new)
                .collect(Collectors.toList());
        return ApiResponse.success(response);
    }

    @PostMapping("/{boardUrl}/subscribe")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> subscribeBoard(@PathVariable String boardUrl,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        boardService.subscribeBoard(userDetails.getUserId(), boardUrl);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{boardUrl}/subscribe")
    public ApiResponse<Void> unsubscribeBoard(@PathVariable String boardUrl,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        boardService.unsubscribeBoard(userDetails.getUserId(), boardUrl);
        return ApiResponse.success(null);
    }

    @PostMapping("/{boardUrl}/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse> createCategory(@PathVariable String boardUrl,
            @Valid @RequestBody CategoryRequest request) {
        BoardCategory category = boardService.createCategory(boardUrl, request);
        return ApiResponse.success(new CategoryResponse(category));
    }

    @PutMapping("/categories/{categoryId}")
    public ApiResponse<CategoryResponse> updateCategory(@PathVariable Long categoryId,
            @Valid @RequestBody CategoryRequest request) {
        BoardCategory category = boardService.updateCategory(categoryId, request);
        return ApiResponse.success(new CategoryResponse(category));
    }

    @DeleteMapping("/categories/{categoryId}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long categoryId) {
        boardService.deleteCategory(categoryId);
        return ApiResponse.success(null);
    }
}
