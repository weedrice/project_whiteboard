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
import com.weedrice.whiteboard.domain.tag.repository.PostTagRepository;
import com.weedrice.whiteboard.domain.tag.service.TagService;
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
    private static final String DEFAULT_INQUIRY_BOARD_URL = "inquiry";
    private static final String DEFAULT_CATEGORY_NAME = "\uC77C\uBC18";

    private final PostRepository postRepository;
    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final UserRepository userRepository;
    private final PostLikeRepository postLikeRepository;
    private final ScrapRepository scrapRepository;
    private final DraftPostRepository draftPostRepository;
    private final PostVersionRepository postVersionRepository;
    private final TagService tagService;
    private final PostTagRepository postTagRepository;
    private final ViewHistoryRepository viewHistoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PointService pointService;
    private final CommentRepository commentRepository;
    private final FileService fileService;
    private final BoardSubscriptionRepository boardSubscriptionRepository;
    private final UserBlockService userBlockService;
    private final GlobalConfigService globalConfigService;
    private final AgentOwnershipService agentOwnershipService;
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
        LocalDateTime since = LocalDateTime.now().minusHours(24);

        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIds(currentUserId);
        }

        List<Post> posts = postRepository.findTrendingPosts(since, blockedUserIds, pageable);
        return postSummaryAssembler.assembleTrendingPosts(posts, currentUserId);
    }

    @Transactional
    public Post getPostById(@NonNull Long postId, Long userId) {
        return getPostById(postId, userId, true);
    }

    @Transactional
    public Post getPostById(@NonNull Long postId, Long userId, boolean incrementView) {
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        User viewer = null;
        boolean authorBlocked = false;
        if (userId != null) {
            List<Long> blockedUserIds = userBlockService.getBlockedUserIds(userId);
            authorBlocked = blockedUserIds != null && blockedUserIds.contains(post.getUser().getUserId());
            viewer = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        }
        postAccessPolicy.validateReadable(post, viewer, authorBlocked);

        if (incrementView) {
            post.incrementViewCount();

            if (viewer != null) {
                User currentViewer = viewer;
                ViewHistory viewHistory = viewHistoryRepository.findByUserAndPost(currentViewer, post)
                        .orElseGet(() -> viewHistoryRepository
                                .save(new ViewHistory(currentViewer, post)));
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

        return PostResponse.from(post, tags, viewHistory, isLiked, isScrapped, imageUrls, isAdmin);
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        ViewHistory viewHistory = viewHistoryRepository.findByUserAndPost(user, post)
                .orElseGet(() -> viewHistoryRepository.save(new ViewHistory(user, post)));

        Comment lastReadComment = null;
        if (request.getLastReadCommentId() != null) {
            lastReadComment = commentRepository.findById(request.getLastReadCommentId()).orElse(null);
        }

        viewHistory.updateView(lastReadComment, request.getDurationMs() != null ? request.getDurationMs() : 0L);
    }

    @Transactional
    public void incrementViewCount(@NonNull Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        post.incrementViewCount();
    }

    @Transactional
    public Post createPost(@NonNull Long userId, @NonNull Long boardId, PostCreateRequest request) {
        return createPost(userId, null, boardId, request);
    }

    @Transactional
    public Post createPost(@NonNull Long userId, Long agentId, @NonNull Long boardId, PostCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
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
            category = boardCategoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
            validateCategoryInBoard(board, category);

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
        tagService.processTagsForPost(savedPost, request.getTags());
        savePostVersion(savedPost, user, "CREATE", null, null);

        if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
            for (Long fileId : request.getFileIds()) {
                fileService.associateFileWithEntity(fileId, userId, savedPost.getPostId(), "POST_CONTENT");
            }
        }

        // ?ъ씤??吏湲?(寃뚯떆湲 ?묒꽦)
        String postCreateRewardStr = globalConfigService.getConfig("POINT_POST_CREATE_REWARD");
        int postCreateReward = postCreateRewardStr != null ? Integer.parseInt(postCreateRewardStr) : 50;
        pointService.addPoint(userId, postCreateReward, "\uAC8C\uC2DC\uAE00 \uC791\uC131", savedPost.getPostId(), "POST");
        return savedPost;
    }

    @Transactional
    public Post updatePost(@NonNull Long userId, @NonNull Long postId, PostUpdateRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (post.getIsDeleted()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        if (!post.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        BoardCategory category = null;
        if (request.getCategoryId() != null) {
            category = boardCategoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
            validateCategoryInBoard(post.getBoard(), category);
        }

        String originalTitle = post.getTitle();
        String originalContents = post.getContents();

        // 蹂몃Ц?먯꽌??sanitize留??곸슜?섍퀬 HTML ?쒓렇??덉슜?쒕떎.
        String sanitizedContents = InputSanitizer.sanitize(request.getContents());

        boolean isSecret = !boardAccessPolicy.isInquiryBoard(post.getBoard()) && request.isSecret();
        post.updatePost(category, request.getTitle(), sanitizedContents, request.isNsfw(),
                request.isSpoiler(), isSecret);
        tagService.processTagsForPost(post, request.getTags());

        if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
            for (Long fileId : request.getFileIds()) {
                fileService.associateFileWithEntity(fileId, userId, post.getPostId(), "POST_CONTENT");
            }
        }

        User modifier = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        savePostVersion(post, modifier, "MODIFY", originalTitle, originalContents);

        return post;
    }

    @Transactional
    public void deletePost(@NonNull Long userId, @NonNull Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (post.getIsDeleted()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        if (!post.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        post.deletePost();
        postTagRepository.findByPost(post).forEach(postTag -> postTag.getTag().decrementPostCount());
        postTagRepository.deleteByPost(post);
        User modifier = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Post post = getPostById(postId, userId, false);

        ScrapId scrapId = new ScrapId(userId, postId);
        if (scrapRepository.existsById(scrapId)) {
            throw new BusinessException(ErrorCode.ALREADY_SCRAPED);
        }

        Scrap scrap = Scrap.builder()
                .user(user)
                .post(post)
                .remark(remark)
                .build();
        scrapRepository.save(scrap);
    }

    @Transactional
    public void unscrapPost(@NonNull Long userId, @NonNull Long postId) {
        ScrapId scrapId = new ScrapId(userId, postId);
        if (!scrapRepository.existsById(scrapId)) {
            throw new BusinessException(ErrorCode.NOT_SCRAPED);
        }
        // Unscrap? 寃뚯떆湲???젣?섏뿀?붾씪??ㅽ겕??紐⑸줉?먯꽌 ?쒓굅 媛?ν빐??쒕떎.
        // ?곕씪??寃뚯떆湲 議댁옱 ?щ?瑜??ㅼ떆 ?뺤씤?섏? ?딄퀬 ?ㅽ겕??ID濡쒕쭔 ??젣?쒕떎.
        scrapRepository.deleteById(scrapId);
    }

    public ScrapListResponse getMyScraps(@NonNull Long userId, @NonNull Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Page<Scrap> scrapPage = scrapRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        return ScrapListResponse.from(scrapPage);
    }

    public DraftListResponse getDraftPosts(@NonNull Long userId, @NonNull Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Page<DraftPost> draftPage = draftPostRepository.findByUserOrderByModifiedAtDesc(user, pageable);
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Board board = boardRepository.findByBoardUrl(request.getBoardUrl()) // boardUrl ?ъ슜
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        validateBoardReadable(board, userId);
        Post originalPost = null;
        if (request.getOriginalPostId() != null) {
            originalPost = postRepository.findById(request.getOriginalPostId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        }

        DraftPost draftPost;
        if (request.getDraftId() != null) {
            draftPost = draftPostRepository.findByDraftIdAndUser(request.getDraftId(), user)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
            draftPost.updateDraft(board, request.getTitle(), request.getContents(), originalPost);
        } else {
            draftPost = DraftPost.builder()
                    .user(user)
                    .board(board)
                    .title(request.getTitle())
                    .contents(request.getContents())
                    .originalPost(originalPost)
                    .build();
        }
        return draftPostRepository.save(draftPost);
    }

    @Transactional
    public void deleteDraftPost(@NonNull Long userId, @NonNull Long draftId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        DraftPost draftPost = draftPostRepository.findByDraftIdAndUser(draftId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
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
        return postTagRepository.findByPost(post).stream()
                .map(postTag -> postTag.getTag().getTagName())
                .collect(Collectors.toList());
    }

    public Page<PostSummary> getRecentlyViewedPosts(@NonNull Long userId, @NonNull Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Page<ViewHistory> historyPage = viewHistoryRepository.findByUserOrderByModifiedAtDesc(user, pageable);
        return postSummaryAssembler.assembleHistoryPage(historyPage);
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

    private void validateCategoryInBoard(Board board, BoardCategory category) {
        if (board == null || category == null || category.getBoard() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        Long boardId = board.getBoardId();
        Long categoryBoardId = category.getBoard().getBoardId();
        if (!Objects.equals(boardId, categoryBoardId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    private void validateBoardWriteRole(Board board, User user) {
        boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(), true).stream()
                .filter(category -> DEFAULT_CATEGORY_NAME.equals(category.getName()))
                .findFirst()
                .ifPresent(category -> validateWriteRole(board, user, category.getMinWriteRole()));
    }

    private void validateWriteRole(Board board, User user, String minRole) {
        if (Role.SUPER_ADMIN.equals(minRole)) {
            if (!user.getIsSuperAdmin()) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            return;
        }

        if (Role.BOARD_ADMIN.equals(minRole) && !boardAccessPolicy.hasBoardAdminAccess(board, user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
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

}

