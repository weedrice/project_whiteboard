package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.dto.AdminBoardResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardManagerCandidateResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardDetailResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardRecentUpdateResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardUpdateRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.dto.SubscriptionBoardResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.util.BoardUrlNormalizer;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.config.AnonymousReadCacheInvalidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class BoardService {

    private final BoardQueryService queryService;
    private final BoardProvisioningService provisioningService;
    private final BoardCommandService boardCommandService;
    private final BoardProvisioningSideEffectService provisioningSideEffectService;
    private final BoardSubscriptionService subscriptionService;
    private final BoardCategoryService categoryService;
    private final BoardAccessPolicy boardAccessPolicy;
    private final BoardVisitService boardVisitService;
    private final BoardAnonymousCacheService anonymousCacheService;
    private final AnonymousReadCacheInvalidator cacheInvalidator;

    public BoardService(BoardQueryService queryService,
                          BoardProvisioningService provisioningService,
                          BoardCommandService boardCommandService,
                          BoardProvisioningSideEffectService provisioningSideEffectService,
                          BoardSubscriptionService subscriptionService,
                           BoardCategoryService categoryService,
                           BoardAccessPolicy boardAccessPolicy,
                           BoardVisitService boardVisitService,
                           BoardAnonymousCacheService anonymousCacheService,
                           AnonymousReadCacheInvalidator cacheInvalidator) {
        this.queryService = queryService;
        this.provisioningService = provisioningService;
        this.boardCommandService = boardCommandService;
        this.provisioningSideEffectService = provisioningSideEffectService;
        this.subscriptionService = subscriptionService;
        this.categoryService = categoryService;
        this.boardAccessPolicy = boardAccessPolicy;
        this.boardVisitService = boardVisitService;
        this.anonymousCacheService = anonymousCacheService;
        this.cacheInvalidator = cacheInvalidator;
    }

    public List<BoardListResponse> getActiveBoards(Long userId) {
        return userId == null ? anonymousCacheService.getActiveBoards() : queryService.getActiveBoards(userId);
    }

    public List<BoardListResponse> getTopBoards(Long userId) {
        return queryService.getTopBoardsByUserId(userId);
    }

    public List<BoardListResponse> getTopBoardsByUserId(Long userId) {
        return queryService.getTopBoardsByUserId(userId);
    }

    public List<BoardListResponse> getTopBoardsByUserId(Long userId, int limit) {
        return userId == null
                ? anonymousCacheService.getTopBoards(limit)
                : queryService.getTopBoardsByUserId(userId, limit);
    }

    public List<BoardListResponse> getRecommendations(List<String> topics, Long userId) {
        List<String> normalizedTopics = topics == null ? List.of() : topics.stream()
                .map(topic -> topic == null ? "" : topic.strip().toLowerCase(Locale.ROOT))
                .filter(topic -> !topic.isBlank())
                .distinct()
                .toList();
        if (normalizedTopics.isEmpty()) {
            return getTopBoardsByUserId(userId, 12);
        }

        return getActiveBoards(userId).stream()
                .filter(board -> matchesTopic(board, normalizedTopics))
                .limit(12)
                .toList();
    }

    public List<AdminBoardResponse> getAllBoards(Long userId) {
        return queryService.getAllBoards(userId);
    }

    public BoardDetailResponse getBoardDetails(String boardUrl, Long userId) {
        String normalizedBoardUrl = validatePublicBoardPath(boardUrl);
        BoardDetailResponse response = userId == null
                ? anonymousCacheService.getBoardDetails(normalizedBoardUrl)
                : queryService.getBoardDetails(normalizedBoardUrl, userId);
        boardVisitService.touchVisit(userId, normalizedBoardUrl);
        return response;
    }

    public List<BoardRecentUpdateResponse> getRecentBoardUpdates(List<String> boardUrls, Long userId) {
        return queryService.getRecentBoardUpdates(boardUrls, userId);
    }

    public List<CategoryResponse> getActiveCategories(String boardUrl, Long userId) {
        String normalizedBoardUrl = validatePublicBoardPath(boardUrl);
        return queryService.getActiveCategories(normalizedBoardUrl, userId);
    }

    public List<PostSummary> getNoticeSummaries(String boardUrl, Long currentUserId) {
        String normalizedBoardUrl = validatePublicBoardPath(boardUrl);
        return queryService.getNoticeSummaries(normalizedBoardUrl, currentUserId);
    }

    @Transactional
    public void ensureInquiryBoard(Long userId, String requestedBoardUrl) {
        provisioningService.ensureInquiryBoard(userId, requestedBoardUrl);
    }

    @Transactional
    public void subscribeBoard(Long userId, String boardUrl) {
        String normalizedBoardUrl = validatePublicBoardPath(boardUrl);
        subscriptionService.subscribeBoard(userId, normalizedBoardUrl);
    }

    @Transactional
    public void unsubscribeBoard(Long userId, String boardUrl) {
        String normalizedBoardUrl = validatePublicBoardPath(boardUrl);
        subscriptionService.unsubscribeBoard(userId, normalizedBoardUrl);
    }

    public Page<SubscriptionBoardResponse> getMySubscriptions(Long userId, Pageable pageable) {
        return queryService.getMySubscriptions(userId, pageable);
    }

    public Page<SubscriptionBoardResponse> getMySubscriptions(Long userId, Pageable pageable, boolean includeUnavailable) {
        return queryService.getMySubscriptions(userId, pageable, includeUnavailable);
    }

    public Page<BoardManagerCandidateResponse> getBoardManagerCandidates(String boardUrl, Long userId,
            String keyword, Pageable pageable) {
        String normalizedBoardUrl = validatePublicBoardPath(boardUrl);
        return queryService.getBoardManagerCandidates(normalizedBoardUrl, userId, keyword, pageable);
    }

    @Transactional
    public BoardCommandResult createBoard(Long creatorId, BoardCreateRequest request) {
        BoardCreateCommandResult result = boardCommandService.createBoard(creatorId, request);
        provisioningSideEffectService.applyCreateSideEffects(result);
        cacheInvalidator.evictBoardRelatedCachesAfterCommit();
        return new BoardCommandResult(result.board().getBoardUrl());
    }

    @Transactional
    public BoardCommandResult updateBoard(String boardUrl, BoardUpdateRequest request, Long userId) {
        String normalizedBoardUrl = validatePublicBoardPath(boardUrl);
        BoardUpdateCommandResult result = boardCommandService.updateBoard(normalizedBoardUrl, request, userId);
        provisioningSideEffectService.applyUpdateSideEffects(result);
        cacheInvalidator.evictBoardRelatedCachesAfterCommit();
        return new BoardCommandResult(result.board().getBoardUrl());
    }

    @Transactional
    public BoardCommandResult transferBoardManager(String boardUrl, String loginId, Long userId) {
        String normalizedBoardUrl = validatePublicBoardPath(boardUrl);
        provisioningService.transferBoardManager(normalizedBoardUrl, loginId, userId);
        cacheInvalidator.evictBoardRelatedCachesAfterCommit();
        return new BoardCommandResult(normalizedBoardUrl);
    }

    @Transactional
    public void deleteBoard(String boardUrl, Long userId) {
        String normalizedBoardUrl = validatePublicBoardPath(boardUrl);
        provisioningService.deleteBoard(normalizedBoardUrl, userId);
        cacheInvalidator.evictBoardRelatedCachesAfterCommit();
    }

    @Transactional
    public CategoryResponse createCategory(String boardUrl, CategoryRequest request, Long userId) {
        String normalizedBoardUrl = validatePublicBoardPath(boardUrl);
        CategoryResponse response = categoryService.createCategory(normalizedBoardUrl, request, userId);
        cacheInvalidator.evictBoardRelatedCachesAfterCommit();
        return response;
    }

    @Transactional
    public CategoryResponse updateCategory(Long categoryId, CategoryRequest request, Long userId) {
        CategoryResponse response = categoryService.updateCategory(categoryId, request, userId);
        cacheInvalidator.evictBoardRelatedCachesAfterCommit();
        return response;
    }

    @Transactional
    public void deleteCategory(Long categoryId, Long userId) {
        categoryService.deleteCategory(categoryId, userId);
        cacheInvalidator.evictBoardRelatedCachesAfterCommit();
    }

    @Transactional
    public List<CategoryResponse> reorderCategories(String boardUrl, List<Long> categoryIds, Long userId) {
        String normalizedBoardUrl = validatePublicBoardPath(boardUrl);
        List<CategoryResponse> response = categoryService.reorderCategories(normalizedBoardUrl, categoryIds, userId);
        cacheInvalidator.evictBoardRelatedCachesAfterCommit();
        return response;
    }

    @Transactional
    public void updateSubscriptionOrder(Long userId, List<String> boardUrls) {
        subscriptionService.updateSubscriptionOrder(userId, boardUrls);
    }

    private String validatePublicBoardPath(String boardUrl) {
        String normalizedBoardUrl = BoardUrlNormalizer.normalizeLookup(boardUrl);
        if (boardAccessPolicy.isInquiryBoardUrl(normalizedBoardUrl)) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }
        return normalizedBoardUrl;
    }

    private boolean matchesTopic(BoardListResponse board, List<String> topics) {
        String boardName = board.getBoardName() == null ? "" : board.getBoardName().toLowerCase(Locale.ROOT);
        String boardUrl = board.getBoardUrl() == null ? "" : board.getBoardUrl().toLowerCase(Locale.ROOT);
        return topics.stream().anyMatch(topic -> boardName.contains(topic) || boardUrl.contains(topic));
    }
}
