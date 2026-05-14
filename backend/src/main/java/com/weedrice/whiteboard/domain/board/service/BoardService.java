package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.dto.AdminBoardResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardDetailResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardUpdateRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
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

    public BoardService(BoardQueryService queryService,
                        BoardProvisioningService provisioningService,
                        BoardSubscriptionService subscriptionService,
                        BoardCategoryService categoryService) {
        this.queryService = queryService;
        this.provisioningService = provisioningService;
        this.subscriptionService = subscriptionService;
        this.categoryService = categoryService;
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

    public List<AdminBoardResponse> getAllBoards(Long userId) {
        return queryService.getAllBoards(userId);
    }

    public BoardDetailResponse getBoardDetails(String boardUrl, Long userId) {
        return queryService.getBoardDetails(boardUrl, userId);
    }

    public List<CategoryResponse> getActiveCategories(String boardUrl, Long userId) {
        return queryService.getActiveCategories(boardUrl, userId);
    }

    public List<PostSummary> getNoticeSummaries(String boardUrl, Long currentUserId) {
        return queryService.getNoticeSummaries(boardUrl, currentUserId);
    }

    @Transactional
    public void ensureInquiryBoard(Long userId, String requestedBoardUrl) {
        provisioningService.ensureInquiryBoard(userId, requestedBoardUrl);
    }

    @Transactional
    public void subscribeBoard(Long userId, String boardUrl) {
        subscriptionService.subscribeBoard(userId, boardUrl);
    }

    @Transactional
    public void unsubscribeBoard(Long userId, String boardUrl) {
        subscriptionService.unsubscribeBoard(userId, boardUrl);
    }

    public Page<BoardListResponse> getMySubscriptions(Long userId, Pageable pageable) {
        return queryService.getMySubscriptions(userId, pageable);
    }

    public Page<BoardListResponse> getMySubscriptions(Long userId, Pageable pageable, boolean includeUnavailable) {
        return queryService.getMySubscriptions(userId, pageable, includeUnavailable);
    }

    @Transactional
    public Board createBoard(Long creatorId, BoardCreateRequest request) {
        return provisioningService.createBoard(creatorId, request);
    }

    @Transactional
    public Board updateBoard(String boardUrl, BoardUpdateRequest request, Long userId) {
        return provisioningService.updateBoard(boardUrl, request, userId);
    }

    @Transactional
    public void transferBoardManager(String boardUrl, String loginId, Long userId) {
        provisioningService.transferBoardManager(boardUrl, loginId, userId);
    }

    @Transactional
    public void deleteBoard(String boardUrl, Long userId) {
        provisioningService.deleteBoard(boardUrl, userId);
    }

    @Transactional
    public CategoryResponse createCategory(String boardUrl, CategoryRequest request, Long userId) {
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
}
