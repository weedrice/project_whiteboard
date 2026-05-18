package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.dto.AdminBoardResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardManagerCandidateResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardDetailResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardUpdateRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.dto.SubscriptionBoardResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class BoardService {

    private final BoardQueryService queryService;
    private final BoardProvisioningService provisioningService;
    private final BoardSubscriptionService subscriptionService;
    private final BoardCategoryService categoryService;
    private final BoardAccessPolicy boardAccessPolicy;

    public BoardService(BoardQueryService queryService,
                         BoardProvisioningService provisioningService,
                         BoardSubscriptionService subscriptionService,
                         BoardCategoryService categoryService,
                         BoardAccessPolicy boardAccessPolicy) {
        this.queryService = queryService;
        this.provisioningService = provisioningService;
        this.subscriptionService = subscriptionService;
        this.categoryService = categoryService;
        this.boardAccessPolicy = boardAccessPolicy;
    }

    public List<BoardListResponse> getActiveBoards(Long userId) {
        return queryService.getActiveBoards(userId);
    }

    public List<BoardListResponse> getTopBoards(Long userId) {
        return queryService.getTopBoardsByUserId(userId);
    }

    public List<BoardListResponse> getTopBoardsByUserId(Long userId) {
        return queryService.getTopBoardsByUserId(userId);
    }

    public List<BoardListResponse> getTopBoardsByUserId(Long userId, int limit) {
        return queryService.getTopBoardsByUserId(userId, limit);
    }

    public List<AdminBoardResponse> getAllBoards(Long userId) {
        return queryService.getAllBoards(userId);
    }

    public BoardDetailResponse getBoardDetails(String boardUrl, Long userId) {
        validatePublicBoardPath(boardUrl);
        return queryService.getBoardDetails(boardUrl, userId);
    }

    public List<CategoryResponse> getActiveCategories(String boardUrl, Long userId) {
        validatePublicBoardPath(boardUrl);
        return queryService.getActiveCategories(boardUrl, userId);
    }

    public List<PostSummary> getNoticeSummaries(String boardUrl, Long currentUserId) {
        validatePublicBoardPath(boardUrl);
        return queryService.getNoticeSummaries(boardUrl, currentUserId);
    }

    @Transactional
    public void ensureInquiryBoard(Long userId, String requestedBoardUrl) {
        provisioningService.ensureInquiryBoard(userId, requestedBoardUrl);
    }

    @Transactional
    public void subscribeBoard(Long userId, String boardUrl) {
        validatePublicBoardPath(boardUrl);
        subscriptionService.subscribeBoard(userId, boardUrl);
    }

    @Transactional
    public void unsubscribeBoard(Long userId, String boardUrl) {
        validatePublicBoardPath(boardUrl);
        subscriptionService.unsubscribeBoard(userId, boardUrl);
    }

    public Page<SubscriptionBoardResponse> getMySubscriptions(Long userId, Pageable pageable) {
        return queryService.getMySubscriptions(userId, pageable);
    }

    public Page<SubscriptionBoardResponse> getMySubscriptions(Long userId, Pageable pageable, boolean includeUnavailable) {
        return queryService.getMySubscriptions(userId, pageable, includeUnavailable);
    }

    public Page<BoardManagerCandidateResponse> getBoardManagerCandidates(String boardUrl, Long userId,
            String keyword, Pageable pageable) {
        validatePublicBoardPath(boardUrl);
        return queryService.getBoardManagerCandidates(boardUrl, userId, keyword, pageable);
    }

    @Transactional
    public BoardCommandResult createBoard(Long creatorId, BoardCreateRequest request) {
        Board board = provisioningService.createBoard(creatorId, request);
        return new BoardCommandResult(board.getBoardUrl());
    }

    @Transactional
    public BoardCommandResult updateBoard(String boardUrl, BoardUpdateRequest request, Long userId) {
        validatePublicBoardPath(boardUrl);
        Board board = provisioningService.updateBoard(boardUrl, request, userId);
        return new BoardCommandResult(board.getBoardUrl());
    }

    @Transactional
    public BoardCommandResult transferBoardManager(String boardUrl, String loginId, Long userId) {
        validatePublicBoardPath(boardUrl);
        provisioningService.transferBoardManager(boardUrl, loginId, userId);
        return new BoardCommandResult(boardUrl);
    }

    @Transactional
    public void deleteBoard(String boardUrl, Long userId) {
        validatePublicBoardPath(boardUrl);
        provisioningService.deleteBoard(boardUrl, userId);
    }

    @Transactional
    public CategoryResponse createCategory(String boardUrl, CategoryRequest request, Long userId) {
        validatePublicBoardPath(boardUrl);
        return categoryService.createCategory(boardUrl, request, userId);
    }

    @Transactional
    public CategoryResponse updateCategory(Long categoryId, CategoryRequest request, Long userId) {
        return categoryService.updateCategory(categoryId, request, userId);
    }

    @Transactional
    public void deleteCategory(Long categoryId, Long userId) {
        categoryService.deleteCategory(categoryId, userId);
    }

    @Transactional
    public void updateSubscriptionOrder(Long userId, List<String> boardUrls) {
        subscriptionService.updateSubscriptionOrder(userId, boardUrls);
    }

    private void validatePublicBoardPath(String boardUrl) {
        if (boardAccessPolicy.isInquiryBoardUrl(boardUrl)) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }
    }
}
