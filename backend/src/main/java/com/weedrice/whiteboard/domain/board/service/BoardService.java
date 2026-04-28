package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.AdminBoardResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardDetailResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardUpdateRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
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

    public List<BoardListResponse> getActiveBoards(UserDetails userDetails) {
        return queryService.getActiveBoards(userDetails);
    }

    public List<BoardListResponse> getTopBoards(UserDetails userDetails) {
        return queryService.getTopBoards(userDetails);
    }

    public List<AdminBoardResponse> getAllBoards(UserDetails userDetails) {
        return queryService.getAllBoards(userDetails);
    }

    public BoardDetailResponse getBoardDetails(String boardUrl, UserDetails userDetails) {
        return queryService.getBoardDetails(boardUrl, userDetails);
    }

    public List<CategoryResponse> getActiveCategories(String boardUrl, UserDetails userDetails) {
        return queryService.getActiveCategories(boardUrl, userDetails);
    }

    public List<PostSummary> getNoticeSummaries(String boardUrl, Long currentUserId) {
        return queryService.getNoticeSummaries(boardUrl, currentUserId);
    }

    @Transactional
    public void ensureInquiryBoard(UserDetails userDetails, String requestedBoardUrl) {
        provisioningService.ensureInquiryBoard(userDetails, requestedBoardUrl);
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
    public Board updateBoard(String boardUrl, BoardUpdateRequest request, UserDetails userDetails) {
        return provisioningService.updateBoard(boardUrl, request, userDetails);
    }

    @Transactional
    public void transferBoardManager(String boardUrl, String loginId, UserDetails userDetails) {
        provisioningService.transferBoardManager(boardUrl, loginId, userDetails);
    }

    @Transactional
    public void deleteBoard(String boardUrl, UserDetails userDetails) {
        provisioningService.deleteBoard(boardUrl, userDetails);
    }

    @Transactional
    public CategoryResponse createCategory(String boardUrl, CategoryRequest request) {
        return categoryService.createCategory(boardUrl, request);
    }

    @Transactional
    public CategoryResponse updateCategory(Long categoryId, CategoryRequest request) {
        return categoryService.updateCategory(categoryId, request);
    }

    @Transactional
    public void deleteCategory(Long categoryId) {
        categoryService.deleteCategory(categoryId);
    }

    @Transactional
    public void updateSubscriptionOrder(Long userId, List<String> boardUrls) {
        subscriptionService.updateSubscriptionOrder(userId, boardUrls);
    }
}
