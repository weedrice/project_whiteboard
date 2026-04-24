package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentOwnershipService;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.user.entity.Role; // Import Role
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.feed.event.PostPublishedEvent;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
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
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.tag.service.TagAssignmentService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService; // Import UserBlockService
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings({ "null" })
public class PostService {
    private static final int DEFAULT_BOARD_PAGE_SIZE = 20;
    private static final String DEFAULT_INQUIRY_BOARD_URL = "inquiry";
    private static final String DEFAULT_CATEGORY_NAME = "\uC77C\uBC18";
    private static final long MAX_VIEW_DURATION_MS = 86_400_000L;

    private final PostRepository postRepository;
    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final UserRepository userRepository;
    private final PostLikeRepository postLikeRepository;
    private final ScrapRepository scrapRepository;
    private final DraftPostRepository draftPostRepository;
    private final PostVersionRepository postVersionRepository;
    private final TagAssignmentService tagAssignmentService;
    private final ViewHistoryRepository viewHistoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PointService pointService;
    private final CommentRepository commentRepository;
    private final FileService fileService;
    private final BoardSubscriptionRepository boardSubscriptionRepository;
    private final UserBlockService userBlockService;
    private final GlobalConfigService globalConfigService;
    private final AgentOwnershipService agentOwnershipService;
    private final SanctionService sanctionService;
    private final PostSummaryAssembler postSummaryAssembler;
    private final PostAccessPolicy postAccessPolicy;
    private final BoardAccessPolicy boardAccessPolicy;

    public Page<PostSummary> getPosts(String boardUrl, Long categoryId, String keyword, Integer minLikes, Long currentUserId,
            @NonNull Pageable pageable) {
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        if (boardAccessPolicy.isInquiryBoard(board)) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }

        validateBoardReadable(board, currentUserId);
        boolean includeSecret = canViewSecretPosts(board, currentUserId);

        Page<Post> posts = this.getPosts(board.getBoardId(), categoryId, keyword, minLikes, currentUserId, includeSecret,
                pageable);
        return postSummaryAssembler.assembleBoardPage(posts, pageable, true, true);
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

        validateBoardReadable(board, currentUserId);
        boolean includeSecret = canViewSecretPosts(board, currentUserId);
        return this.getNotices(board.getBoardId(), currentUserId, includeSecret);
    }

    @Transactional
    public Post createPost(@NonNull Long userId, String boardUrl, PostCreateRequest request) {
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        return this.createPost(userId, null, board.getBoardId(), request);
    }

    @Transactional
    public Post createPostAsAgent(@NonNull Long userId, @NonNull Long agentId, String boardUrl, PostCreateRequest request) {
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        return this.createPost(userId, agentId, board.getBoardId(), request);
    }

    // --- boardId 湲곕컲 public/private 硫붿꽌??---
    public Page<Post> getPosts(Long boardId, Long categoryId, String keyword, Integer minLikes, Long currentUserId,
            Boolean includeSecret, @NonNull Pageable pageable) {
        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIds(currentUserId);
        }
        return postRepository.findByBoardIdAndCategoryId(boardId, categoryId, keyword, minLikes, blockedUserIds, includeSecret,
                currentUserId, pageable);
    }

    public List<Post> getNotices(Long boardId, Long currentUserId, Boolean includeSecret) {
        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIds(currentUserId);
        }
        return postRepository.findNoticesByBoardId(boardId, true, false, blockedUserIds, includeSecret, currentUserId);
    }

    public Page<PostSummary> getPostsByTag(Long tagId, Long currentUserId, @NonNull Pageable pageable) {
        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIds(currentUserId);
        }
        Page<Post> postPage = postRepository.findByTagId(tagId, blockedUserIds, pageable);

        List<Long> postIds = postPage.getContent().stream()
                .map(Post::getPostId)
                .collect(Collectors.toList());

        Set<Long> postIdsWithImages = getPostIdsWithImages(postIds);

        return postPage.map(post -> {
            PostSummary summary = PostSummary.from(post);
            summary.setHasImage(postIdsWithImages.contains(post.getPostId()));
            return summary;
        });
    }

    public Page<PostSummary> getMyPosts(Long userId, @NonNull Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Page<Post> posts = postRepository.findByUserAndIsDeleted(user, false, pageable);
        return postSummaryAssembler.assembleBoardPage(posts, pageable, true, true);
    }

    public Page<PostSummary> getInquiryPostsForAdmin(@NonNull Pageable pageable) {
        Board inquiryBoard = boardRepository.findByBoardUrl(DEFAULT_INQUIRY_BOARD_URL)
                .orElse(null);
        if (inquiryBoard == null) {
            return Page.empty(pageable);
        }

        Page<Post> posts = postRepository.findByBoard_BoardId(inquiryBoard.getBoardId(), pageable);
        return postSummaryAssembler.assembleBoardPage(posts, pageable, false, true);
    }

    public List<PostSummary> getTrendingPosts(Pageable pageable, Long currentUserId) {
        return getTrendingPosts(pageable, currentUserId, "24h");
    }

    public List<PostSummary> getTrendingPosts(Pageable pageable, Long currentUserId, String period) {
        LocalDateTime since = resolveTrendingSince(period);

        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIds(currentUserId);
        }

        List<Post> posts = postRepository.findTrendingPosts(since, blockedUserIds, pageable);
        return postSummaryAssembler.assembleTrendingPosts(posts, currentUserId);
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
        return getPostById(postId, userId, true);
    }

    @Transactional
    public Post getPostById(@NonNull Long postId, Long userId, boolean incrementView) {
        User viewer = getViewer(userId);
        Post post = getReadablePost(postId, viewer);

        if (incrementView) {
            post.incrementViewCount();

            if (viewer != null) {
                ViewHistory viewHistory = getOrCreateViewHistory(viewer, post);
                viewHistory.updateView(null, 0);
            }
        }

        return post;
    }

    @Transactional
    public PostResponse getPostResponse(@NonNull Long postId, Long userId) {
        return getPostResponse(postId, userId, true);
    }

    @Transactional
    public PostResponse getPostResponse(@NonNull Long postId, Long userId, boolean incrementView) {
        Post post = getPostById(postId, userId, incrementView);
        List<String> tags = getTagsForPost(postId);
        boolean isLiked = isPostLikedByUser(postId, userId);
        boolean isScrapped = isPostScrappedByUser(postId, userId);
        ViewHistory viewHistory = getViewHistory(userId, postId);
        List<String> imageUrls = getPostImageUrls(postId);
        boolean isAdmin = isBoardAdmin(userId, post.getBoard().getBoardId());
        int boardListPage = resolveDefaultBoardListPage(post, userId);

        return PostResponse.from(post, tags, viewHistory, isLiked, isScrapped, imageUrls, isAdmin, boardListPage);
    }

    public PostResponse getInquiryPostResponseForAdmin(@NonNull Long postId) {
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!boardAccessPolicy.isInquiryBoard(post.getBoard())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        List<String> tags = getTagsForPost(postId);
        List<String> imageUrls = getPostImageUrls(postId);
        return PostResponse.from(post, tags, null, false, false, imageUrls, true);
    }

    public boolean isPostLikedByUser(@NonNull Long postId, Long userId) {
        if (userId == null) {
            return false;
        }
        return postLikeRepository.existsById(new PostLikeId(userId, postId));
    }

    public boolean isPostScrappedByUser(@NonNull Long postId, Long userId) {
        if (userId == null) {
            return false;
        }
        return scrapRepository.existsById(new ScrapId(userId, postId));
    }

    public ViewHistory getViewHistory(Long userId, @NonNull Long postId) {
        if (userId == null) {
            return null;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        return viewHistoryRepository.findByUserAndPost(user, post).orElse(null);
    }

    @Transactional
    public void updateViewHistory(@NonNull Long userId, @NonNull Long postId, ViewHistoryRequest request) {
        User user = getViewer(userId);
        Post post = getReadablePost(postId, user);
        long durationMs = resolveDurationMs(request);
        Comment lastReadComment = resolveLastReadComment(postId, request.getLastReadCommentId());
        ViewHistory viewHistory = viewHistoryRepository.findByUserAndPost(user, post).orElse(null);
        validateDurationAccumulation(viewHistory != null ? viewHistory.getDurationMs() : 0L, durationMs);
        if (viewHistory == null) {
            viewHistory = createViewHistory(user, post);
        }
        viewHistory.updateView(lastReadComment, durationMs);
    }

    @Transactional
    public void incrementViewCount(@NonNull Long postId) {
        incrementViewCount(postId, null);
    }

    @Transactional
    public void incrementViewCount(@NonNull Long postId, Long userId) {
        Post post = getReadablePost(postId, getViewer(userId));
        post.incrementViewCount();
    }

    private ViewHistory getOrCreateViewHistory(User user, Post post) {
        return viewHistoryRepository.findByUserAndPost(user, post)
                .orElseGet(() -> createViewHistory(user, post));
    }

    private ViewHistory createViewHistory(User user, Post post) {
        try {
            return viewHistoryRepository.saveAndFlush(new ViewHistory(user, post));
        } catch (DataIntegrityViolationException ex) {
            return viewHistoryRepository.findByUserAndPost(user, post)
                    .orElseThrow(() -> ex);
        }
    }

    private long resolveDurationMs(ViewHistoryRequest request) {
        Long durationMs = request.getDurationMs();
        if (durationMs == null) {
            return 0L;
        }
        if (durationMs < 0 || durationMs > MAX_VIEW_DURATION_MS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return durationMs;
    }

    private void validateDurationAccumulation(long currentDurationMs, long durationMs) {
        if (durationMs > 0 && currentDurationMs > Long.MAX_VALUE - durationMs) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private Comment resolveLastReadComment(Long postId, Long lastReadCommentId) {
        if (lastReadCommentId == null) {
            return null;
        }
        return commentRepository.findByCommentIdAndPost_PostId(lastReadCommentId, postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE));
    }

    private User getViewer(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Post getReadablePost(@NonNull Long postId, User viewer) {
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        validateReadable(post, viewer);
        return post;
    }

    private void validateReadable(Post post, User viewer) {
        boolean authorBlocked = false;
        if (viewer != null) {
            List<Long> blockedUserIds = userBlockService.getBlockedUserIds(viewer.getUserId());
            authorBlocked = blockedUserIds != null && blockedUserIds.contains(post.getUser().getUserId());
        }
        postAccessPolicy.validateReadable(post, viewer, authorBlocked);
    }

    @Transactional
    public Post createPost(@NonNull Long userId, @NonNull Long boardId, PostCreateRequest request) {
        return createPost(userId, null, boardId, request);
    }

    @Transactional
    public Post createPost(@NonNull Long userId, Long agentId, @NonNull Long boardId, PostCreateRequest request) {
        User user = getWritableUser(userId);
        sanctionService.validateNotMuted(user);
        Agent agent = agentOwnershipService.resolveOwnedActiveAgent(userId, agentId);
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        boardAccessPolicy.validateWritable(board, user);
        validateBoardWriteRole(board, user);

        if (request.isNotice()) {
            if (!boardAccessPolicy.hasBoardAdminAccess(board, user)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }

        BoardCategory category = null;
        if (request.getCategoryId() != null) {
            category = findActiveCategory(board, request.getCategoryId());
            validateWriteRole(board, user, category.getMinWriteRole());
        }

        // 蹂몃Ц?먯꽌??sanitize留??곸슜?섍퀬 HTML ?쒓렇??덉슜?쒕떎.
        String sanitizedContents = InputSanitizer.sanitize(request.getContents());

        boolean isSecret = !boardAccessPolicy.isInquiryBoard(board) && request.isSecret();

        Post post = Post.builder()
                .board(board)
                .user(user)
                .agent(agent)
                .category(category)
                .title(request.getTitle())
                .contents(sanitizedContents)
                .isNotice(request.isNotice())
                .isNsfw(request.isNsfw())
                .isSpoiler(request.isSpoiler())
                .isSecret(isSecret)
                .build();

        Post savedPost = postRepository.save(post);
        tagAssignmentService.assignTags(savedPost, request.getTags());
        savePostVersion(savedPost, user, "CREATE", null, null);

        if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
            fileService.attachFilesToPost(request.getFileIds(), userId, savedPost.getPostId());
        }

        // ?ъ씤??吏湲?(寃뚯떆湲 ?묒꽦)
        String postCreateRewardStr = globalConfigService.getConfig("POINT_POST_CREATE_REWARD");
        int postCreateReward = postCreateRewardStr != null ? Integer.parseInt(postCreateRewardStr) : 50;
        pointService.addPoint(userId, postCreateReward, "\uAC8C\uC2DC\uAE00 \uC791\uC131", savedPost.getPostId(), "POST");
        eventPublisher.publishEvent(new PostPublishedEvent(savedPost.getPostId(), board.getBoardId()));
        return savedPost;
    }

    @Transactional
    public Post updatePost(@NonNull Long userId, @NonNull Long postId, PostUpdateRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        User modifier = getWritableUser(userId);
        sanctionService.validateNotMuted(modifier);

        if (post.getIsDeleted()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        if (!post.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        BoardCategory category = resolveUpdatedCategory(post, modifier, request.getCategoryId());

        String originalTitle = post.getTitle();
        String originalContents = post.getContents();

        // 蹂몃Ц?먯꽌??sanitize留??곸슜?섍퀬 HTML ?쒓렇??덉슜?쒕떎.
        String sanitizedContents = InputSanitizer.sanitize(request.getContents());

        boolean isSecret = !boardAccessPolicy.isInquiryBoard(post.getBoard()) && request.isSecret();
        post.updatePost(category, request.getTitle(), sanitizedContents, request.isNsfw(),
                request.isSpoiler(), isSecret);
        tagAssignmentService.assignTags(post, request.getTags());

        if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
            fileService.attachFilesToPost(request.getFileIds(), userId, post.getPostId());
        }

        savePostVersion(post, modifier, "MODIFY", originalTitle, originalContents);

        return post;
    }

    @Transactional
    public void deletePost(@NonNull Long userId, @NonNull Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        User modifier = getWritableUser(userId);

        if (post.getIsDeleted()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        if (!post.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        post.deletePost();
        tagAssignmentService.clearTags(post);
        savePostVersion(post, modifier, "DELETE", post.getTitle(), post.getContents());

        // ?ъ씤??李④컧 (寃뚯떆湲 ??젣)
        String postCreateRewardStr = globalConfigService.getConfig("POINT_POST_CREATE_REWARD");
        int postCreateReward = postCreateRewardStr != null ? Integer.parseInt(postCreateRewardStr) : 50;
        pointService.forceSubtractPoint(userId, postCreateReward, "\uAC8C\uC2DC\uAE00 \uC0AD\uC81C", postId, "POST");
    }

    @Transactional
    public int likePost(@NonNull Long userId, @NonNull Long postId) {
        return likePost(userId, null, postId);
    }

    @Transactional
    public int likePost(@NonNull Long userId, Long actorAgentId, @NonNull Long postId) {
        User user = getWritableUser(userId);
        Agent actorAgent = agentOwnershipService.resolveOwnedActiveAgent(userId, actorAgentId);
        Post post = getPostById(postId, userId, false);
        boolean skipNotification = post.getAgent() != null;
        User postOwner = post.getUser();

        PostLike postLike = PostLike.builder()
                .user(user)
                .post(post)
                .build();
        try {
            postLikeRepository.saveAndFlush(postLike);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.ALREADY_LIKED);
        }

        postRepository.incrementLikeCount(postId);
        int likeCount = getPostLikeCount(postId);
        if (skipNotification) {
            return likeCount;
        }

        String content = resolveNotificationActorName(user, actorAgent)
                + "\uB2D8\uC774 \uD68C\uC6D0\uB2D8\uC758 \uAC8C\uC2DC\uAE00\uC744 \uC88B\uC544\uD569\uB2C8\uB2E4.";
        NotificationEvent event = new NotificationEvent(postOwner, user, actorAgent, NotificationType.LIKE, "POST", postId, content);
        eventPublisher.publishEvent(event);

        return likeCount;
    }

    @Transactional
    public int unlikePost(@NonNull Long userId, @NonNull Long postId) {
        getWritableUser(userId);
        getPostById(postId, userId, false);

        int deletedCount = postLikeRepository.deleteByUserIdAndPostId(userId, postId);
        if (deletedCount == 0) {
            throw new BusinessException(ErrorCode.NOT_LIKED);
        }

        postRepository.decrementLikeCount(postId);
        return getPostLikeCount(postId);
    }

    @Transactional
    public void scrapPost(@NonNull Long userId, @NonNull Long postId, String remark) {
        User user = getWritableUser(userId);
        Post post = getPostById(postId, userId, false);
        ScrapId scrapId = new ScrapId(userId, postId);

        Scrap scrap = Scrap.builder()
                .user(user)
                .post(post)
                .remark(remark)
                .build();
        try {
            scrapRepository.saveAndFlush(scrap);
        } catch (DataIntegrityViolationException ex) {
            if (scrapRepository.existsById(scrapId)) {
                throw new BusinessException(ErrorCode.ALREADY_SCRAPED);
            }
            throw ex;
        }
    }

    @Transactional
    public void unscrapPost(@NonNull Long userId, @NonNull Long postId) {
        getWritableUser(userId);
        long deletedCount = scrapRepository.deleteByUser_UserIdAndPost_PostId(userId, postId);
        if (deletedCount == 0) {
            throw new BusinessException(ErrorCode.NOT_SCRAPED);
        }
    }

    public ScrapListResponse getMyScraps(@NonNull Long userId, @NonNull Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Page<Scrap> scrapPage = scrapRepository.findPageByUserWithPostDetails(user, pageable);
        return ScrapListResponse.from(scrapPage);
    }

    public DraftListResponse getDraftPosts(@NonNull Long userId, @NonNull Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Page<DraftPost> draftPage = draftPostRepository.findPageByUserWithBoard(user, pageable);
        return DraftListResponse.from(draftPage);
    }

    public DraftResponse getDraftPost(@NonNull Long userId, @NonNull Long draftId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        DraftPost draftPost = draftPostRepository.findByDraftIdAndUser(draftId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return DraftResponse.from(draftPost);
    }

    @Transactional
    public DraftPost saveDraftPost(@NonNull Long userId, PostDraftRequest request) {
        User user = getWritableUser(userId);
        sanctionService.validateNotMuted(user);
        Board board = boardRepository.findByBoardUrl(request.getBoardUrl()) // boardUrl ?ъ슜
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        boardAccessPolicy.validateWritable(board, user);
        validateBoardWriteRole(board, user);
        BoardCategory category = null;
        if (request.getCategoryId() != null) {
            category = findActiveCategory(board, request.getCategoryId());
            validateWriteRole(board, user, category.getMinWriteRole());
        }
        if (request.isNotice() && !boardAccessPolicy.hasBoardAdminAccess(board, user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Post originalPost = null;
        if (request.getOriginalPostId() != null) {
            originalPost = postRepository.findById(request.getOriginalPostId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        }

        DraftPost draftPost;
        if (request.getDraftId() != null) {
            draftPost = draftPostRepository.findByDraftIdAndUser(request.getDraftId(), user)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
            if (request.getUpdatedAt() != null
                    && draftPost.getModifiedAt() != null
                    && request.getUpdatedAt().isBefore(draftPost.getModifiedAt())) {
                throw new BusinessException(ErrorCode.DRAFT_OUTDATED);
            }
            draftPost.updateDraft(
                    board,
                    category,
                    request.getTitle(),
                    request.getContents(),
                    request.getTags(),
                    request.isNotice(),
                    request.isNsfw(),
                    request.isSpoiler(),
                    request.isSecret(),
                    request.getFileIds(),
                    originalPost);
        } else {
            draftPost = DraftPost.builder()
                    .user(user)
                    .board(board)
                    .category(category)
                    .title(request.getTitle())
                    .contents(request.getContents())
                    .tags(request.getTags())
                    .isNotice(request.isNotice())
                    .isNsfw(request.isNsfw())
                    .isSpoiler(request.isSpoiler())
                    .isSecret(request.isSecret())
                    .fileIds(request.getFileIds())
                    .originalPost(originalPost)
                    .build();
        }
        DraftPost savedDraftPost = draftPostRepository.save(draftPost);
        fileService.syncDraftFiles(request.getFileIds(), userId, savedDraftPost.getDraftId());
        return savedDraftPost;
    }

    @Transactional
    public void deleteDraftPost(@NonNull Long userId, @NonNull Long draftId) {
        User user = getWritableUser(userId);
        DraftPost draftPost = draftPostRepository.findByDraftIdAndUser(draftId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        fileService.markDraftFilesDeletionPending(draftId);
        draftPostRepository.delete(draftPost);
    }

    private void savePostVersion(Post post, User modifier, String versionType, String originalTitle,
            String originalContents) {
        PostVersion postVersion = PostVersion.builder()
                .post(post)
                .modifier(modifier)
                .versionType(versionType)
                .originalTitle(originalTitle)
                .originalContents(originalContents)
                .build();
        postVersionRepository.save(postVersion);
    }

    public List<PostVersionResponse> getPostVersions(@NonNull Long postId, @NonNull Long userId) {
        User viewer = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Post post = getPostById(postId, userId, false);

        boolean isAuthor = post.getUser().getUserId().equals(userId);
        if (!isAuthor && !boardAccessPolicy.hasBoardAdminAccess(post.getBoard(), viewer)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        List<PostVersion> versions = postVersionRepository.findByPost_PostIdOrderByCreatedAtDesc(postId);
        return PostVersionResponse.from(versions);
    }

    public List<String> getTagsForPost(@NonNull Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        return tagAssignmentService.getTagNames(post);
    }

    public Page<PostSummary> getRecentlyViewedPosts(@NonNull Long userId, @NonNull Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Set<Long> blockedUserIds = resolveBlockedUserIds(userId);
        List<Long> blockedUserIdParams = blockedUserIds.isEmpty()
                ? List.of(-1L)
                : new ArrayList<>(blockedUserIds);
        Page<Long> visiblePostIdsPage = viewHistoryRepository.findVisiblePostIdsByUserIdOrderByModifiedAtDesc(
                userId,
                Boolean.TRUE.equals(user.getIsSuperAdmin()),
                blockedUserIds.isEmpty(),
                blockedUserIdParams,
                pageable);

        if (visiblePostIdsPage.isEmpty()) {
            return Page.empty(pageable);
        }

        Map<Long, Post> postsById = postRepository.findByPostIdInAndIsDeletedFalse(visiblePostIdsPage.getContent()).stream()
                .collect(Collectors.toMap(Post::getPostId, post -> post));
        List<Post> orderedPosts = visiblePostIdsPage.getContent().stream()
                .map(postsById::get)
                .filter(Objects::nonNull)
                .toList();
        List<PostSummary> orderedSummaries = postSummaryAssembler.assembleLatestPosts(orderedPosts, userId);

        return new PageImpl<>(orderedSummaries, pageable, visiblePostIdsPage.getTotalElements());
    }

    private User getWritableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        sanctionService.validateNotBanned(user);
        return user;
    }

    public List<String> getPostImageUrls(@NonNull Long postId) {
        return fileService.getFilesByRelatedEntity(postId, "POST_CONTENT").stream()
                .filter(file -> file.getMimeType().startsWith("image/"))
                .map(file -> "/api/v1/files/" + file.getFileId())
                .collect(Collectors.toList());
    }

    public Set<Long> getPostIdsWithImages(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(fileService.getRelatedIdsWithImages(postIds, "POST_CONTENT"));
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
            validateBoardWriteRole(board, user);
            return true;
        } catch (BusinessException exception) {
            if (ErrorCode.FORBIDDEN.equals(exception.getErrorCode())
                    || ErrorCode.BOARD_NOT_FOUND.equals(exception.getErrorCode())) {
                return false;
            }
            throw exception;
        }
    }

    private void validateBoardReadable(Board board, Long userId) {
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        }
        boardAccessPolicy.validateReadable(board, user);
    }

    private boolean canViewSecretPosts(Board board, Long userId) {
        if (userId == null) {
            return false;
        }
        User user = userRepository.findById(userId).orElse(null);
        return boardAccessPolicy.canViewSecretPosts(board, user);
    }

    private BoardCategory findActiveCategory(Board board, Long categoryId) {
        if (board == null || categoryId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(categoryId, board.getBoardId(), true)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private BoardCategory resolveUpdatedCategory(Post post, User modifier, Long categoryId) {
        if (categoryId == null) {
            return null;
        }

        BoardCategory currentCategory = post.getCategory();
        if (currentCategory != null && Objects.equals(currentCategory.getCategoryId(), categoryId)) {
            return currentCategory;
        }

        BoardCategory category = findActiveCategory(post.getBoard(), categoryId);
        validateWriteRole(post.getBoard(), modifier, category.getMinWriteRole());
        return category;
    }

    private void validateBoardWriteRole(Board board, User user) {
        boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(), true).stream()
                .filter(category -> DEFAULT_CATEGORY_NAME.equals(category.getName()))
                .findFirst()
                .ifPresent(category -> validateWriteRole(board, user, category.getMinWriteRole()));
    }

    private void validateWriteRole(Board board, User user, String minRole) {
        String normalizedMinRole = BoardCategory.resolveMinWriteRole(minRole);
        switch (normalizedMinRole) {
            case Role.USER:
                return;
            case Role.BOARD_ADMIN:
                if (!boardAccessPolicy.hasBoardAdminAccess(board, user)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                }
                return;
            case Role.SUPER_ADMIN:
                if (!user.getIsSuperAdmin()) {
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                }
                return;
            default:
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String resolveNotificationActorName(User user, Agent actorAgent) {
        if (actorAgent != null && actorAgent.getName() != null && !actorAgent.getName().isBlank()) {
            return actorAgent.getName();
        }
        return user.getDisplayName();
    }

    private int getPostLikeCount(Long postId) {
        Integer likeCount = postRepository.findLikeCountByPostId(postId);
        if (likeCount == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return likeCount;
    }

    private int resolveDefaultBoardListPage(Post post, Long currentUserId) {
        boolean includeSecret = canViewSecretPosts(post.getBoard(), currentUserId);
        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIds(currentUserId);
        }

        long postsBefore = postRepository.countPostsBeforeInBoardDefaultOrder(
                post.getBoard().getBoardId(),
                post.getCreatedAt(),
                post.getPostId(),
                blockedUserIds,
                includeSecret,
                currentUserId);
        long page = postsBefore / DEFAULT_BOARD_PAGE_SIZE;
        return (int) Math.min(page, Integer.MAX_VALUE);
    }

    public List<PostSummary> getLatestPostsByBoard(Long boardId, int limit, Long currentUserId) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        boolean includeSecret = canViewSecretPosts(board, currentUserId);

        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIds(currentUserId);
        }

        Page<Post> postPage = postRepository.findByBoardIdAndCategoryId(boardId, null, null, null, blockedUserIds,
                includeSecret,
                currentUserId, pageable);
        return postSummaryAssembler.assembleLatestPosts(postPage.getContent(), currentUserId);
    }

    public Map<Long, List<PostSummary>> getLatestPostsByBoards(List<Long> boardIds, int limit, Long currentUserId,
            Set<Long> secretVisibleBoardIds) {
        if (boardIds == null || boardIds.isEmpty() || limit <= 0) {
            return Collections.emptyMap();
        }

        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIds(currentUserId);
        }

        List<Long> latestPostIds = postRepository.findLatestPostIdsByBoardIds(
                boardIds,
                limit,
                blockedUserIds,
                secretVisibleBoardIds,
                currentUserId);
        if (latestPostIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Post> postsById = postRepository.findByPostIdIn(latestPostIds).stream()
                .collect(Collectors.toMap(Post::getPostId, post -> post));

        List<Post> orderedPosts = latestPostIds.stream()
                .map(postsById::get)
                .filter(Objects::nonNull)
                .toList();
        List<PostSummary> summaries = postSummaryAssembler.assembleLatestPosts(orderedPosts, currentUserId);

        Map<Long, List<PostSummary>> latestPostsByBoardId = new LinkedHashMap<>();
        for (PostSummary summary : summaries) {
            latestPostsByBoardId.computeIfAbsent(summary.getBoardId(), ignored -> new ArrayList<>())
                    .add(summary);
        }
        return latestPostsByBoardId;
    }

    public Map<Long, PostSummary> getPostSummariesByIds(List<Long> postIds, Long currentUserId) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }

        User viewer = resolveViewer(currentUserId);
        Set<Long> blockedUserIds = resolveBlockedUserIds(currentUserId);
        List<Long> distinctPostIds = postIds.stream().distinct().toList();

        Map<Long, Post> postsById = postRepository.findByPostIdInAndIsDeletedFalse(distinctPostIds).stream()
                .filter(post -> canReadPostSummary(post, viewer, blockedUserIds))
                .collect(Collectors.toMap(Post::getPostId, post -> post));

        List<Post> orderedPosts = postIds.stream()
                .map(postsById::get)
                .filter(Objects::nonNull)
                .toList();
        if (orderedPosts.isEmpty()) {
            return Collections.emptyMap();
        }

        return postSummaryAssembler.assembleLatestPosts(orderedPosts, currentUserId).stream()
                .collect(Collectors.toMap(PostSummary::getPostId, summary -> summary, (left, right) -> left,
                        LinkedHashMap::new));
    }

    private User resolveViewer(Long currentUserId) {
        if (currentUserId == null) {
            return null;
        }
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Set<Long> resolveBlockedUserIds(Long currentUserId) {
        if (currentUserId == null) {
            return Collections.emptySet();
        }

        List<Long> blockedUserIds = userBlockService.getBlockedUserIds(currentUserId);
        if (blockedUserIds == null || blockedUserIds.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(blockedUserIds);
    }

    private boolean canReadPostSummary(Post post, User viewer, Set<Long> blockedUserIds) {
        boolean authorBlocked = viewer != null && blockedUserIds.contains(post.getUser().getUserId());
        try {
            postAccessPolicy.validateReadable(post, viewer, authorBlocked);
            return true;
        } catch (BusinessException ex) {
            if (ErrorCode.POST_NOT_FOUND.equals(ex.getErrorCode())) {
                return false;
            }
            throw ex;
        }
    }

}

