package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.admin.dto.AdminInquirySummaryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.post.dto.DraftListResponse;
import com.weedrice.whiteboard.domain.post.dto.DraftMatchResponse;
import com.weedrice.whiteboard.domain.post.dto.DraftResponse;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.domain.post.dto.PostCreateResponse;
import com.weedrice.whiteboard.domain.post.dto.PostDraftRequest;
import com.weedrice.whiteboard.domain.post.dto.PostResponse;
import com.weedrice.whiteboard.domain.post.dto.PostSeriesRequest;
import com.weedrice.whiteboard.domain.post.dto.PostSeriesResponse;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.dto.PostUpdateRequest;
import com.weedrice.whiteboard.domain.post.dto.PostVersionResponse;
import com.weedrice.whiteboard.domain.post.dto.ScrapFolderRequest;
import com.weedrice.whiteboard.domain.post.dto.ScrapFolderResponse;
import com.weedrice.whiteboard.domain.post.dto.ScrapListResponse;
import com.weedrice.whiteboard.domain.post.dto.ViewHistoryRequest;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {
    private static final int DEFAULT_BOARD_POST_PAGE_SIZE = 20;

    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final PostDetailReadService postDetailReadService;
    private final PostDetailViewCommandService postDetailViewCommandService;
    private final PostDraftService postDraftService;
    private final PostInteractionService postInteractionService;
    private final PostListReadService postListReadService;
    private final BoardAccessPolicy boardAccessPolicy;
    private final PostAuthorCommandPolicy postAuthorCommandPolicy;
    private final PostCommandService postCommandService;
    private final PostFacadeReadService postFacadeReadService;
    private final PostDetailContextResolver postDetailContextResolver;
    private final PostSeriesService postSeriesService;
    private final PostRelatedReadService postRelatedReadService;
    private final PostManagerModerationService postManagerModerationService;

    public Page<PostSummary> getPosts(String boardUrl, Long categoryId, String keyword, Integer minLikes, Long currentUserId,
            @NonNull Pageable pageable) {
        return postListReadService.getPosts(boardUrl, categoryId, keyword, minLikes, currentUserId, pageable);
    }

    public List<Post> getNotices(String boardUrl, Long currentUserId) {
        return postListReadService.getNotices(boardUrl, currentUserId);
    }

    @Transactional
    public Long createPost(@NonNull Long userId, String boardUrl, PostCreateRequest request) {
        return postCommandService.createPost(userId, boardUrl, request);
    }

    public PostCreateResponse createPostWithResponse(@NonNull Long userId, String boardUrl,
            PostCreateRequest request) {
        return postCommandService.createPostWithResponse(userId, boardUrl, request);
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

    // --- boardId based public/private methods ---
    public Page<Post> getPosts(Long boardId, Long categoryId, String keyword, Integer minLikes, Long currentUserId,
            Boolean includeSecret, @NonNull Pageable pageable) {
        return postListReadService.getPosts(boardId, categoryId, keyword, minLikes, currentUserId, includeSecret,
                pageable);
    }

    public List<Post> getNotices(Long boardId, Long currentUserId, Boolean includeSecret) {
        return postListReadService.getNotices(boardId, currentUserId, includeSecret);
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

    public List<PostSummary> getRelatedPosts(@NonNull Long postId, Long userId, Integer size) {
        return postRelatedReadService.getRelatedPosts(postId, userId, size);
    }

    @Transactional
    public void pinPostByManager(@NonNull Long userId, @NonNull Long postId) {
        postManagerModerationService.pinPost(userId, postId);
    }

    @Transactional
    public void unpinPostByManager(@NonNull Long userId, @NonNull Long postId) {
        postManagerModerationService.unpinPost(userId, postId);
    }

    @Transactional
    public void blindPostByManager(@NonNull Long userId, @NonNull Long postId, String reason) {
        postManagerModerationService.blindPost(userId, postId, reason);
    }

    @Transactional
    public void unblindPostByManager(@NonNull Long userId, @NonNull Long postId) {
        postManagerModerationService.unblindPost(userId, postId);
    }

    private PostResponse getPostResponseInternal(
            @NonNull Long postId,
            Long userId,
            boolean incrementView,
            int boardListPageSize) {
        int normalizedBoardListPageSize = PageRequestUtils.of(0, boardListPageSize).getPageSize();
        if (incrementView) {
            PostDetailContext context = postDetailContextResolver.resolve(postId, userId);
            int viewCount = postDetailViewCommandService.recordReadableView(context);
            return postDetailReadService.getPostResponse(context, normalizedBoardListPageSize, viewCount);
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
    public void scrapPost(@NonNull Long userId, @NonNull Long postId, String remark, Long folderId) {
        postInteractionService.scrapPost(userId, postId, remark, folderId);
    }

    @Transactional
    public void unscrapPost(@NonNull Long userId, @NonNull Long postId) {
        postInteractionService.unscrapPost(userId, postId);
    }

    @Transactional
    public void moveScrap(@NonNull Long userId, @NonNull Long postId, Long folderId) {
        postInteractionService.moveScrap(userId, postId, folderId);
    }

    public ScrapListResponse getMyScraps(@NonNull Long userId, Long folderId, String keyword,
            @NonNull Pageable pageable) {
        return postInteractionService.getMyScraps(userId, folderId, keyword, pageable);
    }

    public ScrapListResponse getMyScraps(@NonNull Long userId, @NonNull Pageable pageable) {
        return getMyScraps(userId, null, null, pageable);
    }

    public List<ScrapFolderResponse> getScrapFolders(@NonNull Long userId) {
        return postInteractionService.getScrapFolders(userId);
    }

    @Transactional
    public ScrapFolderResponse createScrapFolder(@NonNull Long userId, ScrapFolderRequest request) {
        return postInteractionService.createScrapFolder(userId, request);
    }

    @Transactional
    public ScrapFolderResponse updateScrapFolder(@NonNull Long userId, @NonNull Long folderId,
            ScrapFolderRequest request) {
        return postInteractionService.updateScrapFolder(userId, folderId, request);
    }

    @Transactional
    public void deleteScrapFolder(@NonNull Long userId, @NonNull Long folderId) {
        postInteractionService.deleteScrapFolder(userId, folderId);
    }

    public List<PostSeriesResponse> getMySeries(@NonNull Long userId) {
        return postSeriesService.getMySeries(userId);
    }

    @Transactional
    public PostSeriesResponse createSeries(@NonNull Long userId, PostSeriesRequest request) {
        return postSeriesService.createSeries(userId, request);
    }

    @Transactional
    public PostSeriesResponse updateSeries(@NonNull Long userId, @NonNull Long seriesId,
            PostSeriesRequest request) {
        return postSeriesService.updateSeries(userId, seriesId, request);
    }

    @Transactional
    public void deleteSeries(@NonNull Long userId, @NonNull Long seriesId) {
        postSeriesService.deleteSeries(userId, seriesId);
    }

    public DraftListResponse getDraftPosts(@NonNull Long userId, @NonNull Pageable pageable) {
        return postDraftService.getDraftPosts(userId, pageable);
    }

    public DraftResponse getDraftPost(@NonNull Long userId, @NonNull Long draftId) {
        return postDraftService.getDraftPost(userId, draftId);
    }

    public DraftMatchResponse getMatchingDraft(
            @NonNull Long userId, String boardUrl, Long originalPostId, String clientDraftKey) {
        return postDraftService.getMatchingDraft(userId, boardUrl, originalPostId, clientDraftKey);
    }

    @Transactional
    public DraftResponse saveDraftPost(@NonNull Long userId, PostDraftRequest request) {
        return postDraftService.saveDraftPost(userId, request);
    }

    @Transactional
    public void deleteDraftPost(@NonNull Long userId, @NonNull Long draftId, Long expectedVersion) {
        postDraftService.deleteDraftPost(userId, draftId, expectedVersion);
    }

    public List<PostVersionResponse> getPostVersions(@NonNull Long postId, @NonNull Long userId) {
        return postFacadeReadService.getPostVersions(postId, userId);
    }

    public List<String> getTagsForPost(@NonNull Long postId) {
        return postFacadeReadService.getTagsForPost(postId);
    }

    public List<String> getPostImageUrls(@NonNull Long postId) {
        return postFacadeReadService.getPostImageUrls(postId);
    }

    public Set<Long> getPostIdsWithImages(List<Long> postIds) {
        return postFacadeReadService.getPostIdsWithImages(postIds);
    }

    public boolean isBoardAdmin(Long userId, Long boardId) {
        if (userId == null) {
            return false;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        Board board = boardRepository.findById(boardId).orElse(null);
        if (board == null) {
            return false;
        }
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

}

