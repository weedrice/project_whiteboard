package com.weedrice.whiteboard.domain.board.controller;

import com.weedrice.whiteboard.domain.board.dto.AdminBoardResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardManagerCandidateResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardDetailResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardManagerTransferRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardSubscriptionOrderRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardUpdateRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.service.BoardApplicationService;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;
    private final BoardApplicationService boardApplicationService;

    @GetMapping
    public ApiResponse<List<BoardListResponse>> getBoards(@CurrentUserId(required = false) Long userId) {
        return ApiResponse.success(boardService.getActiveBoards(userId));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<AdminBoardResponse>> getAllBoards(@CurrentUserId(required = false) Long userId) {
        return ApiResponse.success(boardService.getAllBoards(userId));
    }

    @GetMapping("/top")
    public ApiResponse<List<BoardListResponse>> getTopBoards(@CurrentUserId(required = false) Long userId) {
        return ApiResponse.success(boardService.getTopBoards(userId));
    }

    @GetMapping("/recommendations")
    public ApiResponse<List<BoardListResponse>> getRecommendations(
            @RequestParam(required = false) List<String> topics,
            @CurrentUserId(required = false) Long userId) {
        return ApiResponse.success(boardService.getRecommendations(topics, userId));
    }

    @GetMapping("/{boardUrl}")
    public ApiResponse<BoardDetailResponse> getBoardDetails(@PathVariable String boardUrl,
            @CurrentUserId(required = false) Long userId) {
        return ApiResponse.success(boardService.getBoardDetails(boardUrl, userId));
    }

    @GetMapping("/{boardUrl}/notices")
    public ApiResponse<List<PostSummary>> getNotices(@PathVariable String boardUrl,
            @CurrentUserId(required = false) Long userId) {
        return ApiResponse.success(boardService.getNoticeSummaries(boardUrl, userId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BoardDetailResponse> createBoard(@Valid @RequestBody BoardCreateRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(boardApplicationService.createBoardDetail(userId, request));
    }

    @PostMapping("/inquiry/ensure")
    public ApiResponse<Void> ensureInquiryBoard(
            @CurrentUserId Long userId,
            @RequestParam(required = false) String boardUrl) {
        boardService.ensureInquiryBoard(userId, boardUrl);
        return ApiResponses.ok();
    }

    @PutMapping("/{boardUrl}")
    public ApiResponse<BoardDetailResponse> updateBoard(@PathVariable String boardUrl,
            @Valid @RequestBody BoardUpdateRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(boardApplicationService.updateBoardDetail(
                boardUrl,
                request,
                userId));
    }

    @PutMapping("/{boardUrl}/manager")
    public ApiResponse<BoardDetailResponse> transferBoardManager(@PathVariable String boardUrl,
            @Valid @RequestBody BoardManagerTransferRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(boardApplicationService.transferBoardManagerDetail(
                boardUrl,
                request.getLoginId(),
                userId));
    }

    @GetMapping("/{boardUrl}/manager-candidates")
    public ApiResponse<PageResponse<BoardManagerCandidateResponse>> getBoardManagerCandidates(
            @PathVariable String boardUrl,
            @RequestParam(required = false, name = "q") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentUserId Long userId) {
        return ApiResponses.page(boardService.getBoardManagerCandidates(
                boardUrl,
                userId,
                keyword,
                PageRequestUtils.of(page, size)));
    }

    @DeleteMapping("/{boardUrl}")
    public ApiResponse<Void> deleteBoard(@PathVariable String boardUrl,
            @CurrentUserId Long userId) {
        boardService.deleteBoard(boardUrl, userId);
        return ApiResponses.ok();
    }

    @GetMapping("/{boardUrl}/categories")
    public ApiResponse<List<CategoryResponse>> getCategories(@PathVariable String boardUrl,
            @CurrentUserId(required = false) Long userId) {
        return ApiResponse.success(boardService.getActiveCategories(boardUrl, userId));
    }

    @PostMapping("/{boardUrl}/subscribe")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> subscribeBoard(@PathVariable String boardUrl,
            @CurrentUserId Long userId) {
        boardService.subscribeBoard(userId, boardUrl);
        return ApiResponses.ok();
    }

    @DeleteMapping("/{boardUrl}/subscribe")
    public ApiResponse<Void> unsubscribeBoard(@PathVariable String boardUrl,
            @CurrentUserId Long userId) {
        boardService.unsubscribeBoard(userId, boardUrl);
        return ApiResponses.ok();
    }

    @PostMapping("/{boardUrl}/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse> createCategory(@PathVariable String boardUrl,
            @Valid @RequestBody CategoryRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(boardService.createCategory(boardUrl, request, userId));
    }

    @PutMapping("/categories/{categoryId}")
    public ApiResponse<CategoryResponse> updateCategory(@PathVariable Long categoryId,
            @Valid @RequestBody CategoryRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(boardService.updateCategory(categoryId, request, userId));
    }

    @DeleteMapping("/categories/{categoryId}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long categoryId,
            @CurrentUserId Long userId) {
        boardService.deleteCategory(categoryId, userId);
        return ApiResponses.ok();
    }

    @PutMapping("/subscriptions/order")
    public ApiResponse<Void> updateSubscriptionOrder(
            @Valid @RequestBody BoardSubscriptionOrderRequest request,
            @CurrentUserId Long userId) {
        boardService.updateSubscriptionOrder(userId, request.boardUrls());
        return ApiResponses.ok();
    }
}
