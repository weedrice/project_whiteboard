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
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.weedrice.whiteboard.global.security.AuthenticatedUserResolver.optionalUserId;
import static com.weedrice.whiteboard.global.security.AuthenticatedUserResolver.requiredUserId;

@RestController
@RequestMapping("/api/v1/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;
    private final BoardApplicationService boardApplicationService;

    @GetMapping
    public ApiResponse<List<BoardListResponse>> getBoards(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(boardService.getActiveBoards(optionalUserId(userDetails)));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<AdminBoardResponse>> getAllBoards(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(boardService.getAllBoards(requiredUserId(userDetails)));
    }

    @GetMapping("/top")
    public ApiResponse<List<BoardListResponse>> getTopBoards(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(boardService.getTopBoards(optionalUserId(userDetails)));
    }

    @GetMapping("/{boardUrl}")
    public ApiResponse<BoardDetailResponse> getBoardDetails(@PathVariable String boardUrl,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(boardService.getBoardDetails(boardUrl, optionalUserId(userDetails)));
    }

    @GetMapping("/{boardUrl}/notices")
    public ApiResponse<List<PostSummary>> getNotices(@PathVariable String boardUrl,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(boardService.getNoticeSummaries(boardUrl, optionalUserId(userDetails)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BoardDetailResponse> createBoard(@Valid @RequestBody BoardCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(boardApplicationService.createBoardDetail(requiredUserId(userDetails), request));
    }

    @PostMapping("/inquiry/ensure")
    public ApiResponse<Void> ensureInquiryBoard(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String boardUrl) {
        boardService.ensureInquiryBoard(requiredUserId(userDetails), boardUrl);
        return ApiResponse.success(null);
    }

    @PutMapping("/{boardUrl}")
    public ApiResponse<BoardDetailResponse> updateBoard(@PathVariable String boardUrl,
            @Valid @RequestBody BoardUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(boardApplicationService.updateBoardDetail(
                boardUrl,
                request,
                requiredUserId(userDetails)));
    }

    @PutMapping("/{boardUrl}/manager")
    public ApiResponse<BoardDetailResponse> transferBoardManager(@PathVariable String boardUrl,
            @Valid @RequestBody BoardManagerTransferRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(boardApplicationService.transferBoardManagerDetail(
                boardUrl,
                request.getLoginId(),
                requiredUserId(userDetails)));
    }

    @GetMapping("/{boardUrl}/manager-candidates")
    public ApiResponse<PageResponse<BoardManagerCandidateResponse>> getBoardManagerCandidates(
            @PathVariable String boardUrl,
            @RequestParam(required = false, name = "q") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(new PageResponse<>(boardService.getBoardManagerCandidates(
                boardUrl,
                requiredUserId(userDetails),
                keyword,
                PageRequestUtils.of(page, size))));
    }

    @DeleteMapping("/{boardUrl}")
    public ApiResponse<Void> deleteBoard(@PathVariable String boardUrl,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        boardService.deleteBoard(boardUrl, requiredUserId(userDetails));
        return ApiResponse.success(null);
    }

    @GetMapping("/{boardUrl}/categories")
    public ApiResponse<List<CategoryResponse>> getCategories(@PathVariable String boardUrl,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(boardService.getActiveCategories(boardUrl, optionalUserId(userDetails)));
    }

    @PostMapping("/{boardUrl}/subscribe")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> subscribeBoard(@PathVariable String boardUrl,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        boardService.subscribeBoard(requiredUserId(userDetails), boardUrl);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{boardUrl}/subscribe")
    public ApiResponse<Void> unsubscribeBoard(@PathVariable String boardUrl,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        boardService.unsubscribeBoard(requiredUserId(userDetails), boardUrl);
        return ApiResponse.success(null);
    }

    @PostMapping("/{boardUrl}/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse> createCategory(@PathVariable String boardUrl,
            @Valid @RequestBody CategoryRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(boardService.createCategory(boardUrl, request, requiredUserId(userDetails)));
    }

    @PutMapping("/categories/{categoryId}")
    public ApiResponse<CategoryResponse> updateCategory(@PathVariable Long categoryId,
            @Valid @RequestBody CategoryRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(boardService.updateCategory(categoryId, request, requiredUserId(userDetails)));
    }

    @DeleteMapping("/categories/{categoryId}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long categoryId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        boardService.deleteCategory(categoryId, requiredUserId(userDetails));
        return ApiResponse.success(null);
    }

    @PutMapping("/subscriptions/order")
    public ApiResponse<Void> updateSubscriptionOrder(
            @Valid @RequestBody BoardSubscriptionOrderRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        boardService.updateSubscriptionOrder(requiredUserId(userDetails), request.boardUrls());
        return ApiResponse.success(null);
    }
}
