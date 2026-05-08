package com.weedrice.whiteboard.domain.board.controller;

import com.weedrice.whiteboard.domain.board.dto.AdminBoardResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardDetailResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardManagerTransferRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardSubscriptionOrderRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardUpdateRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;
    private final BoardAccessPolicy boardAccessPolicy;

    @GetMapping
    public ApiResponse<List<BoardListResponse>> getBoards(@AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(boardService.getActiveBoards(userDetails));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<AdminBoardResponse>> getAllBoards(@AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(boardService.getAllBoards(userDetails));
    }

    @GetMapping("/top")
    public ApiResponse<List<BoardListResponse>> getTopBoards(@AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(boardService.getTopBoards(userDetails));
    }

    @GetMapping("/{boardUrl}")
    public ApiResponse<BoardDetailResponse> getBoardDetails(@PathVariable String boardUrl,
            @AuthenticationPrincipal UserDetails userDetails) {
        validateBlockedInquiryBoardPath(boardUrl);
        return ApiResponse.success(boardService.getBoardDetails(boardUrl, userDetails));
    }

    @GetMapping("/{boardUrl}/notices")
    public ApiResponse<List<PostSummary>> getNotices(@PathVariable String boardUrl,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        validateBlockedInquiryBoardPath(boardUrl);
        Long userId = (userDetails != null) ? userDetails.getUserId() : null;
        return ApiResponse.success(boardService.getNoticeSummaries(boardUrl, userId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BoardDetailResponse> createBoard(@Valid @RequestBody BoardCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Board board = boardService.createBoard(userDetails.getUserId(), request);
        return ApiResponse.success(boardService.getBoardDetails(board.getBoardUrl(), userDetails));
    }

    @PostMapping("/inquiry/ensure")
    public ApiResponse<Void> ensureInquiryBoard(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String boardUrl) {
        boardService.ensureInquiryBoard(userDetails, boardUrl);
        return ApiResponse.success(null);
    }

    @PutMapping("/{boardUrl}")
    public ApiResponse<BoardDetailResponse> updateBoard(@PathVariable String boardUrl,
            @Valid @RequestBody BoardUpdateRequest request, @AuthenticationPrincipal UserDetails userDetails) {
        validateBlockedInquiryBoardPath(boardUrl);
        Board updatedBoard = boardService.updateBoard(boardUrl, request, userDetails);
        return ApiResponse.success(boardService.getBoardDetails(updatedBoard.getBoardUrl(), userDetails));
    }

    @PutMapping("/{boardUrl}/manager")
    public ApiResponse<BoardDetailResponse> transferBoardManager(@PathVariable String boardUrl,
            @Valid @RequestBody BoardManagerTransferRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        validateBlockedInquiryBoardPath(boardUrl);
        boardService.transferBoardManager(boardUrl, request.getLoginId(), userDetails);
        return ApiResponse.success(boardService.getBoardDetails(boardUrl, userDetails));
    }

    @DeleteMapping("/{boardUrl}")
    public ApiResponse<Void> deleteBoard(@PathVariable String boardUrl,
            @AuthenticationPrincipal UserDetails userDetails) {
        validateBlockedInquiryBoardPath(boardUrl);
        boardService.deleteBoard(boardUrl, userDetails);
        return ApiResponse.success(null);
    }

    @GetMapping("/{boardUrl}/categories")
    public ApiResponse<List<CategoryResponse>> getCategories(@PathVariable String boardUrl,
            @AuthenticationPrincipal UserDetails userDetails) {
        validateBlockedInquiryBoardPath(boardUrl);
        return ApiResponse.success(boardService.getActiveCategories(boardUrl, userDetails));
    }

    @PostMapping("/{boardUrl}/subscribe")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> subscribeBoard(@PathVariable String boardUrl,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        validateBlockedInquiryBoardPath(boardUrl);
        boardService.subscribeBoard(userDetails.getUserId(), boardUrl);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{boardUrl}/subscribe")
    public ApiResponse<Void> unsubscribeBoard(@PathVariable String boardUrl,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        validateBlockedInquiryBoardPath(boardUrl);
        boardService.unsubscribeBoard(userDetails.getUserId(), boardUrl);
        return ApiResponse.success(null);
    }

    @PostMapping("/{boardUrl}/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse> createCategory(@PathVariable String boardUrl,
            @Valid @RequestBody CategoryRequest request) {
        validateBlockedInquiryBoardPath(boardUrl);
        return ApiResponse.success(boardService.createCategory(boardUrl, request));
    }

    @PutMapping("/categories/{categoryId}")
    public ApiResponse<CategoryResponse> updateCategory(@PathVariable Long categoryId,
            @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.success(boardService.updateCategory(categoryId, request));
    }

    @DeleteMapping("/categories/{categoryId}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long categoryId) {
        boardService.deleteCategory(categoryId);
        return ApiResponse.success(null);
    }

    @PutMapping("/subscriptions/order")
    public ApiResponse<Void> updateSubscriptionOrder(
            @Valid @RequestBody BoardSubscriptionOrderRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        boardService.updateSubscriptionOrder(userDetails.getUserId(), request.boardUrls());
        return ApiResponse.success(null);
    }

    private void validateBlockedInquiryBoardPath(String boardUrl) {
        if (boardAccessPolicy.isInquiryBoardUrl(boardUrl)) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }
    }
}
