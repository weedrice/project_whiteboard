package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
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
import com.weedrice.whiteboard.domain.search.service.SearchRecordEventPublisher;
import com.weedrice.whiteboard.domain.search.service.SearchRequestNormalizer;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings({ "null" })
public class PostService {
    private static final String DEFAULT_INQUIRY_BOARD_URL = "inquiry";
    private static final int DEFAULT_BOARD_POST_PAGE_SIZE = 20;
    private static final Sort DEFAULT_BOARD_POST_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("postId"));
    private static final Sort DEFAULT_TAG_POST_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("postId"));
    private static final Sort DEFAULT_MY_POST_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("postId"));
    private static final Sort DEFAULT_INQUIRY_POST_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final Set<String> BOARD_POST_SORT_PROPERTIES = Set.of(
            "createdAt", "postId", "viewCount", "likeCount");
    private static final Set<String> TAG_POST_SORT_PROPERTIES = Set.of(
            "createdAt", "postId", "viewCount", "likeCount");
    private static final Set<String> MY_POST_SORT_PROPERTIES = Set.of(
            "createdAt", "postId", "viewCount", "likeCount");
    private static final Set<String> INQUIRY_POST_SORT_PROPERTIES = Set.of(
            "createdAt", "postId", "viewCount", "likeCount");

    private final PostRepository postRepository;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final PostReadContextResolver postReadContextResolver;
    private final PostSummaryAssembler postSummaryAssembler;
    private final PostDetailReadService postDetailReadService;
    private final PostDraftService postDraftService;
    private final PostInteractionService postInteractionService;
    private final BoardAccessPolicy boardAccessPolicy;
    private final PostAuthorCommandPolicy postAuthorCommandPolicy;
    private final PostCommandService postCommandService;
    private final PostLatestReadService postLatestReadService;
    private final PostFacadeReadService postFacadeReadService;
    private final SearchRecordEventPublisher searchRecordEventPublisher;

    public Page<PostSummary> getPosts(String boardUrl, Long categoryId, String keyword, Integer minLikes, Long currentUserId,
            @NonNull Pageable pageable) {
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        if (boardAccessPolicy.isInquiryBoard(board)) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }

        PostReadContext context = postReadContextResolver.resolveForBoards(currentUserId, List.of(board));
        validateBoardReadable(board, context);
        boolean includeSecret = context.canViewSecretPosts(board, boardAccessPolicy);
        String canonicalKeyword = SearchRequestNormalizer.canonicalizeOptionalKeyword(keyword);

        Page<Post> posts = this.getPosts(
                board.getBoardId(),
                categoryId,
                canonicalKeyword,
                minLikes,
                context,
                includeSecret,
                pageable);
        Page<PostSummary> response = postSummaryAssembler.assembleBoardPage(posts, posts.getPageable(), true, true);
        publishSearchRecord(currentUserId, canonicalKeyword);
        return response;
    }

    public List<PostSummary> getNoticeSummaries(String boardUrl, Long currentUserId) {
        List<Post> notices = getNotices(boardUrl, currentUserId);
        return notices.stream().map(PostSummary::from).collect(Collectors.toList());
    }

    public List<Post> getNotices(String boardUrl, Long currentUserId) {
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        if (boardAccessPolicy.isInquiryBoard(board)) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }

        PostReadContext context = postReadContextResolver.resolveForBoards(currentUserId, List.of(board));
        validateBoardReadable(board, context);
        boolean includeSecret = context.canViewSecretPosts(board, boardAccessPolicy);
        return this.getNotices(board.getBoardId(), context, includeSecret);
    }

    @Transactional
    public Post createPost(@NonNull Long userId, String boardUrl, PostCreateRequest request) {
        return postCommandService.createPost(userId, boardUrl, request);
    }

    @Transactional
    public Post createPostAsAgent(@NonNull Long userId, @NonNull Long agentId, String boardUrl, PostCreateRequest request) {
        return postCommandService.createPostAsAgent(userId, agentId, boardUrl, request);
    }

    // --- boardId 湲곕컲 public/private 硫붿꽌??---
    public Page<Post> getPosts(Long boardId, Long categoryId, String keyword, Integer minLikes, Long currentUserId,
            Boolean includeSecret, @NonNull Pageable pageable) {
        return getPosts(boardId, categoryId, keyword, minLikes, postReadContextResolver.resolveQueryParameters(currentUserId),
                includeSecret, pageable);
    }

    private Page<Post> getPosts(Long boardId, Long categoryId, String keyword, Integer minLikes,
            PostReadContext context, Boolean includeSecret, @NonNull Pageable pageable) {
        Pageable safePageable = normalizeBoardPostPageable(pageable);
        String canonicalKeyword = SearchRequestNormalizer.canonicalizeOptionalKeyword(keyword);
        return postRepository.findByBoardIdAndCategoryId(
                boardId,
                categoryId,
                canonicalKeyword,
                minLikes,
                context.blockedUserIds(),
                includeSecret,
                context.viewerUserId(),
                safePageable);
    }

    private Pageable normalizeBoardPostPageable(Pageable pageable) {
        Pageable normalizedPageable = PageRequestUtils.of(
                pageable,
                DEFAULT_BOARD_POST_PAGE_SIZE,
                DEFAULT_BOARD_POST_SORT,
                BOARD_POST_SORT_PROPERTIES);
        if (normalizedPageable.getSort().getOrderFor("postId") != null) {
            return normalizedPageable;
        }
        Sort stableSort = normalizedPageable.getSort().and(Sort.by(Sort.Order.desc("postId")));
        return PageRequestUtils.of(normalizedPageable.getPageNumber(), normalizedPageable.getPageSize(), stableSort);
    }

    private void publishSearchRecord(Long currentUserId, String keyword) {
        String canonicalKeyword = SearchRequestNormalizer.canonicalizeOptionalKeyword(keyword);
        if (canonicalKeyword == null) {
            return;
        }
        searchRecordEventPublisher.publish(currentUserId, canonicalKeyword);
    }

    public List<Post> getNotices(Long boardId, Long currentUserId, Boolean includeSecret) {
        return getNotices(boardId, postReadContextResolver.resolveQueryParameters(currentUserId), includeSecret);
    }

    private List<Post> getNotices(Long boardId, PostReadContext context, Boolean includeSecret) {
        return postRepository.findNoticesByBoardId(
                boardId,
                true,
                false,
                context.blockedUserIds(),
                includeSecret,
                context.viewerUserId());
    }

    public Page<PostSummary> getPostsByTag(Long tagId, Long currentUserId, @NonNull Pageable pageable) {
        Pageable safePageable = sanitizeTagPostPageable(pageable);
        PostReadContext context = postReadContextResolver.resolve(currentUserId);
        Page<Post> postPage = postRepository.findByTagId(tagId, context.blockedUserIds(), safePageable);
        return postSummaryAssembler.assembleTagPage(postPage);
    }

    private Pageable sanitizeTagPostPageable(Pageable pageable) {
        Sort safeSort = sanitizeTagPostSort(pageable.getSort());
        if (pageable.isUnpaged()) {
            return PageRequest.of(0, PageRequestUtils.DEFAULT_MAX_PAGE_SIZE, safeSort);
        }
        return PageRequestUtils.of(pageable.getPageNumber(), pageable.getPageSize(), safeSort);
    }

    private Sort sanitizeTagPostSort(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return DEFAULT_TAG_POST_SORT;
        }
        boolean allAllowed = StreamSupport.stream(sort.spliterator(), false)
                .allMatch(order -> TAG_POST_SORT_PROPERTIES.contains(order.getProperty()));
        Sort safeSort = allAllowed ? sort : DEFAULT_TAG_POST_SORT;
        if (safeSort.getOrderFor("postId") != null) {
            return safeSort;
        }
        return safeSort.and(Sort.by(Sort.Order.desc("postId")));
    }

    public Page<PostSummary> getMyPosts(Long userId, @NonNull Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Pageable safePageable = PageRequestUtils.of(
                pageable,
                DEFAULT_BOARD_POST_PAGE_SIZE,
                DEFAULT_MY_POST_SORT,
                MY_POST_SORT_PROPERTIES);
        Page<Post> posts = postRepository.findByUserAndIsDeleted(user, false, safePageable);
        return postSummaryAssembler.assembleBoardPage(posts, safePageable, true, true);
    }

    public Page<PostSummary> getInquiryPostsForAdmin(@NonNull Pageable pageable) {
        Pageable safePageable = PageRequestUtils.of(
                pageable,
                20,
                DEFAULT_INQUIRY_POST_SORT,
                INQUIRY_POST_SORT_PROPERTIES);
        Board inquiryBoard = boardRepository.findByBoardUrl(DEFAULT_INQUIRY_BOARD_URL)
                .orElse(null);
        if (inquiryBoard == null) {
            return Page.empty(safePageable);
        }

        Page<Post> posts = postRepository.findByBoard_BoardIdAndIsDeletedFalse(inquiryBoard.getBoardId(), safePageable);
        return postSummaryAssembler.assembleBoardPage(posts, safePageable, false, true);
    }

    public List<PostSummary> getTrendingPosts(Pageable pageable, Long currentUserId) {
        return getTrendingPosts(pageable, currentUserId, "24h");
    }

    public List<PostSummary> getTrendingPosts(Pageable pageable, Long currentUserId, String period) {
        LocalDateTime since = resolveTrendingSince(period);

        PostReadContext context = postReadContextResolver.resolve(currentUserId);

        List<Post> posts = postRepository.findTrendingPosts(since, context.blockedUserIds(), pageable);
        return postSummaryAssembler.assembleTrendingPosts(posts, currentUserId);
    }

    public Page<PostSummary> getTrendingPostsPage(Pageable pageable, Long currentUserId, String period) {
        LocalDateTime since = resolveTrendingSince(period);

        PostReadContext context = postReadContextResolver.resolve(currentUserId);

        List<Post> fetchedPosts = postRepository.findTrendingPosts(
                since,
                context.blockedUserIds(),
                pageable.getOffset(),
                pageable.getPageSize());
        List<PostSummary> summaries = postSummaryAssembler.assembleTrendingPosts(fetchedPosts, currentUserId);
        long total = postRepository.countTrendingPosts(since, context.blockedUserIds());
        return new PageImpl<>(summaries, pageable, total);
    }

    private LocalDateTime resolveTrendingSince(String period) {
        return switch ((period == null ? "" : period.trim().toLowerCase(Locale.ROOT))) {
            case "7d" -> LocalDateTime.now().minusDays(7);
            case "30d" -> LocalDateTime.now().minusDays(30);
            case "24h", "" -> LocalDateTime.now().minusHours(24);
            default -> LocalDateTime.now().minusHours(24);
        };
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
    public Post createPost(@NonNull Long userId, @NonNull Long boardId, PostCreateRequest request) {
        return postCommandService.createPost(userId, boardId, request);
    }

    @Transactional
    public Post createPost(@NonNull Long userId, Long agentId, @NonNull Long boardId, PostCreateRequest request) {
        return postCommandService.createPost(userId, agentId, boardId, request);
    }

    @Transactional
    public Post updatePost(@NonNull Long userId, @NonNull Long postId, PostUpdateRequest request) {
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
    public DraftPost saveDraftPost(@NonNull Long userId, PostDraftRequest request) {
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
            boardAccessPolicy.validateWritable(board, user);
            postAuthorCommandPolicy.validateAppliedCategoryWriteRole(board, user, null);
            return true;
        } catch (BusinessException exception) {
            if (ErrorCode.FORBIDDEN.equals(exception.getErrorCode())
                    || ErrorCode.BOARD_NOT_FOUND.equals(exception.getErrorCode())) {
                return false;
            }
            throw exception;
        }
    }

    private void validateBoardReadable(Board board, PostReadContext context) {
        if (!boardAccessPolicy.canReadBoard(board, context.viewer(), context.activeAdminBoardIds())) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }
    }

    public List<PostSummary> getLatestPostsByBoard(Long boardId, int limit, Long currentUserId) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        PostReadContext context = postReadContextResolver.resolveForBoards(currentUserId, List.of(board));
        boolean includeSecret = context.canViewSecretPosts(board, boardAccessPolicy);

        Page<Post> postPage = postRepository.findByBoardIdAndCategoryId(boardId, null, null, null, context.blockedUserIds(),
                includeSecret,
                context.viewerUserId(), pageable);
        return postSummaryAssembler.assembleLatestPosts(postPage.getContent(), currentUserId);
    }

    public Map<Long, List<PostSummary>> getLatestPostsByBoards(List<Long> boardIds, int limit, Long currentUserId,
            Set<Long> secretVisibleBoardIds) {
        return postLatestReadService.getLatestPostsByBoards(boardIds, limit, currentUserId, secretVisibleBoardIds);
    }

    public Map<Long, PostSummary> getPostSummariesByIds(List<Long> postIds, Long currentUserId) {
        return postFacadeReadService.getPostSummariesByIds(postIds, currentUserId);
    }

}

