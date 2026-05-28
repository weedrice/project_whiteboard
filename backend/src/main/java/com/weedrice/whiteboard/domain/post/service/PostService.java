package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.admin.dto.AdminInquirySummaryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.feed.dto.FeedPostSummary;
import com.weedrice.whiteboard.domain.post.dto.DraftListResponse;
import com.weedrice.whiteboard.domain.post.dto.DraftResponse;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.domain.post.dto.PostDraftRequest;
import com.weedrice.whiteboard.domain.post.dto.PostResponse; // Import PostResponse
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.dto.PostUpdateRequest;
import com.weedrice.whiteboard.domain.post.dto.PostVersionResponse;
import com.weedrice.whiteboard.domain.post.dto.ScrapListResponse;
import com.weedrice.whiteboard.domain.post.dto.ViewHistoryRequest;
import com.weedrice.whiteboard.domain.post.entity.*;
import com.weedrice.whiteboard.domain.post.repository.*;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings({ "null" })
public class PostService {
    private static final int DEFAULT_BOARD_POST_PAGE_SIZE = 20;

    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final PostDetailReadService postDetailReadService;
    private final PostDraftService postDraftService;
    private final PostInteractionService postInteractionService;
    private final PostListReadService postListReadService;
    private final BoardAccessPolicy boardAccessPolicy;
    private final PostAuthorCommandPolicy postAuthorCommandPolicy;
    private final PostCommandService postCommandService;
    private final PostFacadeReadService postFacadeReadService;

    public Page<PostSummary> getPosts(String boardUrl, Long categoryId, String keyword, Integer minLikes, Long currentUserId,
            @NonNull Pageable pageable) {
        return postListReadService.getPosts(boardUrl, categoryId, keyword, minLikes, currentUserId, pageable);
    }

    public List<PostSummary> getNoticeSummaries(String boardUrl, Long currentUserId) {
        return postListReadService.getNoticeSummaries(boardUrl, currentUserId);
    }

    public List<PostSummary> getNoticeSummaries(Long boardId, Long currentUserId, Boolean includeSecret) {
        return postListReadService.getNoticeSummaries(boardId, currentUserId, includeSecret);
    }

    public List<Post> getNotices(String boardUrl, Long currentUserId) {
        return postListReadService.getNotices(boardUrl, currentUserId);
    }

    @Transactional
    public Long createPost(@NonNull Long userId, String boardUrl, PostCreateRequest request) {
        return postCommandService.createPost(userId, boardUrl, request);
    }

    @Transactional
    public Long createPostAsAgent(@NonNull Long userId, @NonNull Long agentId, String boardUrl, PostCreateRequest request) {
        return postCommandService.createPostAsAgent(userId, agentId, boardUrl, request);
    }

    @Transactional
    public Long createPostAsAgent(@NonNull Long userId, @NonNull Long agentId, PostCreateRequest request,
            PostCreateContext context) {
        return postCommandService.createPostAsAgent(userId, agentId, request, context);
    }

    // --- boardId 湲곕컲 public/private 硫붿꽌??---
    public Page<Post> getPosts(Long boardId, Long categoryId, String keyword, Integer minLikes, Long currentUserId,
            Boolean includeSecret, @NonNull Pageable pageable) {
        return postListReadService.getPosts(boardId, categoryId, keyword, minLikes, currentUserId, includeSecret,
                pageable);
    }

    public List<Post> getNotices(Long boardId, Long currentUserId, Boolean includeSecret) {
        return postListReadService.getNotices(boardId, currentUserId, includeSecret);
    }

    public Page<PostSummary> getPostsByTag(Long tagId, Long currentUserId, @NonNull Pageable pageable) {
        return postListReadService.getPostsByTag(tagId, currentUserId, pageable);
    }

    public Page<PostSummary> getMyPosts(Long userId, @NonNull Pageable pageable) {
        return postListReadService.getMyPosts(userId, pageable);
    }

    public Page<AdminInquirySummaryResponse> getInquiryPostsForAdmin(@NonNull Pageable pageable) {
        return postListReadService.getInquiryPostsForAdmin(pageable);
    }

    public List<PostSummary> getTrendingPosts(Pageable pageable, Long currentUserId) {
        return postListReadService.getTrendingPosts(pageable, currentUserId);
    }

    public List<PostSummary> getTrendingPosts(Pageable pageable, Long currentUserId, String period) {
        return postListReadService.getTrendingPosts(pageable, currentUserId, period);
    }

    public Page<PostSummary> getTrendingPostsPage(Pageable pageable, Long currentUserId, String period) {
        return postListReadService.getTrendingPostsPage(pageable, currentUserId, period);
    }

    public List<PostSummary> getPublicLandingLatestPosts(Pageable pageable, Long currentUserId) {
        return postListReadService.getPublicLandingLatestPosts(pageable, currentUserId);
    }

    public List<FeedPostSummary> getTrendingFeedPosts(Pageable pageable, Long currentUserId, String period) {
        return postListReadService.getTrendingFeedPosts(pageable, currentUserId, period);
    }

    public List<FeedPostSummary> getPublicLandingLatestFeedPosts(Pageable pageable, Long currentUserId) {
        return postListReadService.getPublicLandingLatestFeedPosts(pageable, currentUserId);
    }

    @Transactional
    public Post getPostById(@NonNull Long postId, Long userId) {
        return postInteractionService.getPostById(postId, userId);
    }

    @Transactional
    public Post getPostById(@NonNull Long postId, Long userId, boolean incrementView) {
        return postInteractionService.getPostById(postId, userId, incrementView);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PostResponse getPostResponse(@NonNull Long postId, Long userId) {
        return getPostResponseInternal(postId, userId, true, DEFAULT_BOARD_POST_PAGE_SIZE);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PostResponse getPostResponse(@NonNull Long postId, Long userId, boolean incrementView) {
        return getPostResponseInternal(postId, userId, incrementView, DEFAULT_BOARD_POST_PAGE_SIZE);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PostResponse getPostResponse(@NonNull Long postId, Long userId, boolean incrementView,
            int boardListPageSize) {
        return getPostResponseInternal(postId, userId, incrementView, boardListPageSize);
    }

    private PostResponse getPostResponseInternal(
            @NonNull Long postId,
            Long userId,
            boolean incrementView,
            int boardListPageSize) {
        int normalizedBoardListPageSize = PageRequestUtils.of(0, boardListPageSize).getPageSize();
        if (incrementView) {
            return postDetailReadService.getPostResponseWithViewIncrement(
                    postId,
                    userId,
                    normalizedBoardListPageSize);
        }
        return postDetailReadService.getPostResponse(postId, userId, normalizedBoardListPageSize);
    }

    public PostResponse getInquiryPostResponseForAdmin(@NonNull Long postId) {
        return postFacadeReadService.getInquiryPostResponseForAdmin(postId);
    }

    public boolean isPostLikedByUser(@NonNull Long postId, Long userId) {
        return postInteractionService.isPostLikedByUser(postId, userId);
    }

    public boolean isPostScrappedByUser(@NonNull Long postId, Long userId) {
        return postInteractionService.isPostScrappedByUser(postId, userId);
    }

    public ViewHistory getViewHistory(Long userId, @NonNull Long postId) {
        return postInteractionService.getViewHistory(userId, postId);
    }

    @Transactional
    public void updateViewHistory(@NonNull Long userId, @NonNull Long postId, ViewHistoryRequest request) {
        postInteractionService.updateViewHistory(userId, postId, request);
    }

    @Transactional
    public void incrementViewCount(@NonNull Long postId) {
        postInteractionService.incrementViewCount(postId);
    }

    @Transactional
    public void incrementViewCount(@NonNull Long postId, Long userId) {
        postInteractionService.incrementViewCount(postId, userId);
    }

    @Transactional
    public Long createPost(@NonNull Long userId, @NonNull Long boardId, PostCreateRequest request) {
        return postCommandService.createPost(userId, boardId, request);
    }

    @Transactional
    public Long createPost(@NonNull Long userId, Long agentId, @NonNull Long boardId, PostCreateRequest request) {
        return postCommandService.createPost(userId, agentId, boardId, request);
    }

    @Transactional
    public Long updatePost(@NonNull Long userId, @NonNull Long postId, PostUpdateRequest request) {
        return postCommandService.updatePost(userId, postId, request);
    }

    @Transactional
    public void deletePost(@NonNull Long userId, @NonNull Long postId) {
        postCommandService.deletePost(userId, postId);
    }

    @Transactional
    public int likePost(@NonNull Long userId, @NonNull Long postId) {
        return postInteractionService.likePost(userId, postId);
    }

    @Transactional
    public int likePost(@NonNull Long userId, Long actorAgentId, @NonNull Long postId) {
        return postInteractionService.likePost(userId, actorAgentId, postId);
    }

    @Transactional
    public int likePost(@NonNull Long userId, Agent actorAgent, @NonNull Post post) {
        return postInteractionService.likePost(userId, actorAgent, post);
    }

    @Transactional
    public int unlikePost(@NonNull Long userId, @NonNull Long postId) {
        return postInteractionService.unlikePost(userId, postId);
    }

    @Transactional
    public void scrapPost(@NonNull Long userId, @NonNull Long postId, String remark) {
        postInteractionService.scrapPost(userId, postId, remark);
    }

    @Transactional
    public void unscrapPost(@NonNull Long userId, @NonNull Long postId) {
        postInteractionService.unscrapPost(userId, postId);
    }

    public ScrapListResponse getMyScraps(@NonNull Long userId, @NonNull Pageable pageable) {
        return postInteractionService.getMyScraps(userId, pageable);
    }

    public DraftListResponse getDraftPosts(@NonNull Long userId, @NonNull Pageable pageable) {
        return postDraftService.getDraftPosts(userId, pageable);
    }

    public DraftResponse getDraftPost(@NonNull Long userId, @NonNull Long draftId) {
        return postDraftService.getDraftPost(userId, draftId);
    }

    @Transactional
    public DraftResponse saveDraftPost(@NonNull Long userId, PostDraftRequest request) {
        return postDraftService.saveDraftPost(userId, request);
    }

    @Transactional
    public void deleteDraftPost(@NonNull Long userId, @NonNull Long draftId) {
        postDraftService.deleteDraftPost(userId, draftId);
    }

    public List<PostVersionResponse> getPostVersions(@NonNull Long postId, @NonNull Long userId) {
        return postFacadeReadService.getPostVersions(postId, userId);
    }

    public List<String> getTagsForPost(@NonNull Long postId) {
        return postFacadeReadService.getTagsForPost(postId);
    }

    public Page<PostSummary> getRecentlyViewedPosts(@NonNull Long userId, @NonNull Pageable pageable) {
        return postInteractionService.getRecentlyViewedPosts(userId, pageable);
    }

    public List<String> getPostImageUrls(@NonNull Long postId) {
        return postFacadeReadService.getPostImageUrls(postId);
    }

    public Set<Long> getPostIdsWithImages(List<Long> postIds) {
        return postFacadeReadService.getPostIdsWithImages(postIds);
    }

    public boolean isBoardAdmin(Long userId, Long boardId) {
        if (userId == null)
            return false;
        User user = userRepository.findById(userId).orElse(null);
        if (user == null)
            return false;
        Board board = boardRepository.findById(boardId).orElse(null);
        if (board == null)
            return false;
        return boardAccessPolicy.hasBoardAdminAccess(board, user);
    }

    public boolean canWriteToBoard(Long userId, Board board) {
        if (userId == null || board == null) {
            return false;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }

        try {
            return postAuthorCommandPolicy.canWriteBoardWithDefaultCategory(board, user, null);
        } catch (BusinessException exception) {
            if (ErrorCode.FORBIDDEN.equals(exception.getErrorCode())
                    || ErrorCode.BOARD_NOT_FOUND.equals(exception.getErrorCode())) {
                return false;
            }
            throw exception;
        }
    }

    public List<PostSummary> getLatestPostsByBoard(Long boardId, int limit, Long currentUserId) {
        return postListReadService.getLatestPostsByBoard(boardId, limit, currentUserId);
    }

    public Map<Long, List<PostSummary>> getLatestPostsByBoards(List<Long> boardIds, int limit, Long currentUserId,
            Set<Long> secretVisibleBoardIds) {
        return postListReadService.getLatestPostsByBoards(boardIds, limit, currentUserId, secretVisibleBoardIds);
    }

    public Map<Long, PostSummary> getPostSummariesByIds(List<Long> postIds, Long currentUserId) {
        return postFacadeReadService.getPostSummariesByIds(postIds, currentUserId);
    }

    public Map<Long, PostSummary> getPostSummariesByIds(List<Long> postIds, PostSummaryReadContext readContext) {
        return postFacadeReadService.getPostSummariesByIds(postIds, readContext);
    }

}

