package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.admin.dto.AdminInquirySummaryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.feed.dto.FeedPostSummary;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostListSummaryProjection;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.search.service.SearchRecordEventPublisher;
import com.weedrice.whiteboard.domain.search.service.SearchRequestNormalizer;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostListReadService {

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
    private final FeedPostSummaryAssembler feedPostSummaryAssembler;
    private final BoardAccessPolicy boardAccessPolicy;
    private final PostLatestReadService postLatestReadService;
    private final SearchRecordEventPublisher searchRecordEventPublisher;

    public Page<PostSummary> getPosts(String boardUrl, Long categoryId, String keyword, Integer minLikes,
            Long currentUserId, @NonNull Pageable pageable) {
        BoardReadContext boardContext = resolveReadableBoardContext(boardUrl, currentUserId);
        String canonicalKeyword = SearchRequestNormalizer.canonicalizeOptionalKeyword(keyword);

        Page<PostListSummaryProjection> posts = getPostListSummaries(
                boardContext.board().getBoardId(),
                categoryId,
                canonicalKeyword,
                minLikes,
                boardContext.context(),
                boardContext.includeSecret(),
                pageable);
        Page<PostSummary> response = postSummaryAssembler.assembleBoardListProjectionPage(
                posts,
                posts.getPageable(),
                true,
                true);
        publishSearchRecord(currentUserId, canonicalKeyword);
        return response;
    }

    public List<PostSummary> getNoticeSummaries(String boardUrl, Long currentUserId) {
        List<Post> notices = getNotices(boardUrl, currentUserId);
        return toNoticeSummaries(notices);
    }

    public List<Post> getNotices(String boardUrl, Long currentUserId) {
        BoardReadContext boardContext = resolveReadableBoardContext(boardUrl, currentUserId);
        return getNotices(
                boardContext.board().getBoardId(),
                boardContext.context(),
                boardContext.includeSecret());
    }

    public Page<Post> getPosts(Long boardId, Long categoryId, String keyword, Integer minLikes, Long currentUserId,
            Boolean includeSecret, @NonNull Pageable pageable) {
        return getPosts(boardId, categoryId, keyword, minLikes,
                postReadContextResolver.resolveQueryParameters(currentUserId), includeSecret, pageable);
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

    private Page<PostListSummaryProjection> getPostListSummaries(Long boardId, Long categoryId, String keyword,
            Integer minLikes, PostReadContext context, Boolean includeSecret, @NonNull Pageable pageable) {
        Pageable safePageable = normalizeBoardPostPageable(pageable);
        String canonicalKeyword = SearchRequestNormalizer.canonicalizeOptionalKeyword(keyword);
        return postRepository.findPostListSummariesByBoardIdAndCategoryId(
                boardId,
                categoryId,
                canonicalKeyword,
                minLikes,
                context.blockedUserIds(),
                includeSecret,
                context.viewerUserId(),
                safePageable);
    }

    public List<Post> getNotices(Long boardId, Long currentUserId, Boolean includeSecret) {
        return getNotices(boardId, postReadContextResolver.resolveQueryParameters(currentUserId), includeSecret);
    }

    public List<PostSummary> getNoticeSummaries(Long boardId, Long currentUserId, Boolean includeSecret) {
        List<Post> notices = getNotices(boardId, currentUserId, includeSecret);
        return toNoticeSummaries(notices);
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

    private List<PostSummary> toNoticeSummaries(List<Post> notices) {
        return notices.stream()
                .map(PostSummary::from)
                .collect(Collectors.toList());
    }

    public Page<PostSummary> getPostsByTag(Long tagId, Long currentUserId, @NonNull Pageable pageable) {
        Pageable safePageable = sanitizeTagPostPageable(pageable);
        PostReadContext context = postReadContextResolver.resolve(currentUserId);
        Page<Post> postPage = postRepository.findByTagId(tagId, context.blockedUserIds(), safePageable);
        return postSummaryAssembler.assembleTagPage(postPage);
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

    public Page<AdminInquirySummaryResponse> getInquiryPostsForAdmin(@NonNull Pageable pageable) {
        Pageable safePageable = PageRequestUtils.of(
                pageable,
                20,
                DEFAULT_INQUIRY_POST_SORT,
                INQUIRY_POST_SORT_PROPERTIES);
        Board inquiryBoard = boardRepository.findByBoardUrl(boardAccessPolicy.getInquiryBoardUrl())
                .orElse(null);
        if (inquiryBoard == null) {
            return Page.empty(safePageable);
        }

        Page<Post> posts = postRepository.findByBoard_BoardIdAndIsDeletedFalse(inquiryBoard.getBoardId(), safePageable);
        return postSummaryAssembler.assembleAdminInquiryPage(posts);
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

    public List<PostSummary> getPublicLandingLatestPosts(Pageable pageable, Long currentUserId) {
        PostReadContext context = postReadContextResolver.resolve(currentUserId);
        List<Post> posts = postRepository.findPublicLandingLatestPosts(
                boardAccessPolicy.getInquiryBoardUrl(),
                context.blockedUserIds(),
                pageable);
        return postSummaryAssembler.assembleLatestPosts(posts, currentUserId);
    }

    public List<FeedPostSummary> getTrendingFeedPosts(Pageable pageable, Long currentUserId, String period) {
        LocalDateTime since = resolveTrendingSince(period);
        PostReadContext context = postReadContextResolver.resolve(currentUserId);
        List<Post> posts = postRepository.findTrendingPosts(since, context.blockedUserIds(), pageable);
        return feedPostSummaryAssembler.assembleTrendingPosts(posts, currentUserId);
    }

    public List<FeedPostSummary> getPublicLandingLatestFeedPosts(Pageable pageable, Long currentUserId) {
        PostReadContext context = postReadContextResolver.resolve(currentUserId);
        List<Post> posts = postRepository.findPublicLandingLatestPosts(
                boardAccessPolicy.getInquiryBoardUrl(),
                context.blockedUserIds(),
                pageable);
        return feedPostSummaryAssembler.assembleLatestPosts(posts, currentUserId);
    }

    public List<PostSummary> getLatestPostsByBoard(Long boardId, int limit, Long currentUserId) {
        Pageable pageable = PageRequestUtils.of(0, limit, DEFAULT_BOARD_POST_SORT);
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        PostReadContext context = postReadContextResolver.resolveForBoards(currentUserId, List.of(board));
        validateBoardReadable(board, context);
        boolean includeSecret = context.canViewSecretPosts(board, boardAccessPolicy);

        Page<Post> postPage = postRepository.findByBoardIdAndCategoryId(boardId, null, null, null,
                context.blockedUserIds(),
                includeSecret,
                context.viewerUserId(),
                pageable);
        return postSummaryAssembler.assembleLatestPosts(postPage.getContent(), currentUserId);
    }

    public Map<Long, List<PostSummary>> getLatestPostsByBoards(List<Long> boardIds, int limit,
            Long currentUserId, Set<Long> secretVisibleBoardIds) {
        return postLatestReadService.getLatestPostsByBoards(boardIds, limit, currentUserId, secretVisibleBoardIds);
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

    private LocalDateTime resolveTrendingSince(String period) {
        return switch ((period == null ? "" : period.trim().toLowerCase(Locale.ROOT))) {
            case "7d" -> LocalDateTime.now().minusDays(7);
            case "30d" -> LocalDateTime.now().minusDays(30);
            case "24h", "" -> LocalDateTime.now().minusHours(24);
            default -> LocalDateTime.now().minusHours(24);
        };
    }

    private BoardReadContext resolveReadableBoardContext(String boardUrl, Long currentUserId) {
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        if (boardAccessPolicy.isInquiryBoard(board)) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }

        PostReadContext context = postReadContextResolver.resolveForBoards(currentUserId, List.of(board));
        validateBoardReadable(board, context);
        return new BoardReadContext(
                board,
                context,
                context.canViewSecretPosts(board, boardAccessPolicy));
    }

    private void validateBoardReadable(Board board, PostReadContext context) {
        if (!boardAccessPolicy.canReadBoard(board, context.viewer(), context.activeAdminBoardIds())) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }
    }

    private record BoardReadContext(Board board, PostReadContext context, boolean includeSecret) {
    }
}
