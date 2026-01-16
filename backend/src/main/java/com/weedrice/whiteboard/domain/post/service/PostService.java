package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.user.entity.Role; // Import Role
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
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
    private final AdminRepository adminRepository;
    private final PointService pointService;
    private final CommentRepository commentRepository;
    private final FileService fileService;
    private final BoardSubscriptionRepository boardSubscriptionRepository;
    private final UserBlockService userBlockService;
    private final GlobalConfigService globalConfigService;

    // --- boardUrl 기반 public 메서드 (오버로드) ---
    public Page<PostSummary> getPosts(String boardUrl, Long categoryId, Integer minLikes, Long currentUserId,
            @NonNull Pageable pageable) {
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        if (!board.getIsActive()) {
            boolean isSuperAdmin = false;
            boolean isBoardAdmin = false;
            boolean isCreator = false;

            if (currentUserId != null) {
                User currentUser = userRepository.findById(currentUserId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                isSuperAdmin = currentUser.getIsSuperAdmin();
                isBoardAdmin = adminRepository.findByUserAndBoardAndIsActive(currentUser, board, true)
                        .isPresent();
                isCreator = board.getCreator().getUserId().equals(currentUser.getUserId());
            }

            if (!isSuperAdmin && !isBoardAdmin && !isCreator) {
                throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
            }
        }

        Page<Post> posts = this.getPosts(board.getBoardId(), categoryId, minLikes, currentUserId, pageable);

        List<PostSummary> summaries = new ArrayList<>();
        long totalElements = posts.getTotalElements();
        int pageNumber = posts.getNumber();
        int pageSize = posts.getSize();

        boolean isAscending = pageable.getSort().stream()
                .anyMatch(order -> order.getProperty().equals("createdAt") && order.isAscending()
                        || order.getProperty().equals("postId") && order.isAscending());

        List<Long> postIds = posts.getContent().stream().map(Post::getPostId)
                .collect(Collectors.toList());
        Set<Long> postIdsWithImages = getPostIdsWithImages(postIds);

        for (int i = 0; i < posts.getContent().size(); i++) {
            Post post = posts.getContent().get(i);
            PostSummary summary = PostSummary.from(post);
            summary.setHasImage(postIdsWithImages.contains(post.getPostId()));

            if (isAscending) {
                summary.setRowNum(((long) pageNumber * pageSize) + i + 1);
            } else {
                summary.setRowNum(totalElements - ((long) pageNumber * pageSize) - i);
            }
            summaries.add(summary);
        }

        return new PageImpl<>(summaries, pageable, totalElements);
    }

    public List<PostSummary> getNoticeSummaries(String boardUrl, Long currentUserId) {
        List<Post> notices = getNotices(boardUrl, currentUserId);
        return notices.stream().map(PostSummary::from).collect(Collectors.toList());
    }

    public List<Post> getNotices(String boardUrl, Long currentUserId) {
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        if (!board.getIsActive()) {
            boolean isSuperAdmin = false;
            boolean isBoardAdmin = false;
            boolean isCreator = false;

            if (currentUserId != null) {
                User currentUser = userRepository.findById(currentUserId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                isSuperAdmin = currentUser.getIsSuperAdmin();
                isBoardAdmin = adminRepository.findByUserAndBoardAndIsActive(currentUser, board, true)
                        .isPresent();
                isCreator = board.getCreator().getUserId().equals(currentUser.getUserId());
            }

            if (!isSuperAdmin && !isBoardAdmin && !isCreator) {
                throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
            }
        }

        return this.getNotices(board.getBoardId(), currentUserId);
    }

    @Transactional
    public Post createPost(@NonNull Long userId, String boardUrl, PostCreateRequest request) {
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        return this.createPost(userId, board.getBoardId(), request);
    }

    // --- 기존 boardId 기반 public/private 메서스 ---
    public Page<Post> getPosts(Long boardId, Long categoryId, Integer minLikes, Long currentUserId,
            @NonNull Pageable pageable) {
        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIds(currentUserId);
        }
        return postRepository.findByBoardIdAndCategoryId(boardId, categoryId, minLikes, blockedUserIds,
                pageable);
    }

    public List<Post> getNotices(Long boardId, Long currentUserId) {
        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIds(currentUserId);
        }
        return postRepository.findNoticesByBoardId(boardId, true, false, blockedUserIds);
    }

    public Page<PostSummary> getPostsByTag(Long tagId, Long currentUserId, @NonNull Pageable pageable) {
        Page<com.weedrice.whiteboard.domain.tag.entity.PostTag> postTags = postTagRepository
                .findByTag_TagId(tagId, pageable);

        List<Long> postIds = postTags.getContent().stream()
                .map(pt -> pt.getPost().getPostId())
                .collect(Collectors.toList());

        Set<Long> postIdsWithImages = getPostIdsWithImages(postIds);

        return postTags.map(pt -> {
            Post post = pt.getPost();
            PostSummary summary = PostSummary.from(post);
            summary.setHasImage(postIdsWithImages.contains(post.getPostId()));
            return summary;
        });
    }

    public Page<PostSummary> getMyPosts(Long userId, @NonNull Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Page<Post> posts = postRepository.findByUserAndIsDeleted(user, false, pageable);

        List<Long> postIds = posts.getContent().stream()
                .map(Post::getPostId)
                .collect(Collectors.toList());

        Set<Long> postIdsWithImages = getPostIdsWithImages(postIds);

        long totalElements = posts.getTotalElements();
        int pageNumber = posts.getNumber();
        int pageSize = posts.getSize();

        boolean isAscending = pageable.getSort().stream()
                .anyMatch(order -> order.getProperty().equals("createdAt") && order.isAscending()
                        || order.getProperty().equals("postId") && order.isAscending());

        List<PostSummary> summaries = new ArrayList<>();
        for (int i = 0; i < posts.getContent().size(); i++) {
            Post post = posts.getContent().get(i);
            PostSummary summary = PostSummary.from(post);
            summary.setHasImage(postIdsWithImages.contains(post.getPostId()));

            if (isAscending) {
                summary.setRowNum(((long) pageNumber * pageSize) + i + 1);
            } else {
                summary.setRowNum(totalElements - ((long) pageNumber * pageSize) - i);
            }
            summaries.add(summary);
        }

        return new PageImpl<>(summaries, pageable, totalElements);
    }

    public List<PostSummary> getTrendingPosts(Pageable pageable, Long currentUserId) {
        LocalDateTime since = LocalDateTime.now().minusHours(24);

        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIds(currentUserId);
        }

        List<Post> posts = postRepository.findTrendingPosts(since, blockedUserIds, pageable);

        if (posts.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> postIds = posts.stream().map(Post::getPostId).collect(Collectors.toList());
        List<Board> boards = posts.stream().map(Post::getBoard).distinct().collect(Collectors.toList());

        Set<Long> postIdsWithImages = getPostIdsWithImages(postIds);

        // Batch fetch user interactions if logged in
        Set<Long> likedPostIds = new HashSet<>();
        Set<Long> scrappedPostIds = new HashSet<>();
        Set<String> subscribedBoardUrls = new HashSet<>();

        if (currentUserId != null) {
            User user = userRepository.findById(currentUserId).orElse(null);
            if (user != null) {
                likedPostIds = postLikeRepository.findByUserAndPostIn(user, posts).stream()
                        .map(like -> like.getPost().getPostId())
                        .collect(Collectors.toSet());

                scrappedPostIds = scrapRepository.findByUserAndPostIn(user, posts).stream()
                        .map(scrap -> scrap.getPost().getPostId())
                        .collect(Collectors.toSet());

                subscribedBoardUrls = boardSubscriptionRepository.findByUserAndBoardIn(user, boards)
                        .stream()
                        .map(sub -> sub.getBoard().getBoardUrl())
                        .collect(Collectors.toSet());
            }
        }

        final Set<Long> finalLikedPostIds = likedPostIds;
        final Set<Long> finalScrappedPostIds = scrappedPostIds;
        final Set<String> finalSubscribedBoardUrls = subscribedBoardUrls;
        return posts.stream()
                .map(post -> {
                    String summary = post.getContents().replaceAll("<[^>]*>", "").trim();
                    if (summary.length() > 1000) {
                        summary = summary.substring(0, 1000);
                    }
                    return PostSummary.from(
                            post,
                            postIdsWithImages.contains(post.getPostId()) ? "/api/v1/files/"
                                    + fileService.getOneImageFileIdForPost(
                                            post.getPostId())
                                    : null,
                            post.getBoard().getIconUrl(),
                            finalLikedPostIds.contains(post.getPostId()),
                            finalScrappedPostIds.contains(post.getPostId()),
                            finalSubscribedBoardUrls
                                    .contains(post.getBoard().getBoardUrl()),
                            postIdsWithImages.contains(post.getPostId()),
                            summary);
                })
                .collect(Collectors.toList());

    }

    @Transactional
    public Post getPostById(@NonNull Long postId, Long userId) {
        return getPostById(postId, userId, true);
    }

    @Transactional
    public Post getPostById(@NonNull Long postId, Long userId, boolean incrementView) {
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (userId != null) {
            List<Long> blockedUserIds = userBlockService.getBlockedUserIds(userId);
            if (blockedUserIds.contains(post.getUser().getUserId())) {
                throw new BusinessException(ErrorCode.POST_NOT_FOUND);
            }
        }

        // Access Control for Inactive Boards
        if (!post.getBoard().getIsActive()) {
            if (userId == null) {
                throw new BusinessException(ErrorCode.POST_NOT_FOUND);
            }
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

            boolean isBoardAdmin = adminRepository
                    .findByUserAndBoardAndIsActive(user, post.getBoard(), true).isPresent();
            boolean isAuthor = post.getUser().getUserId().equals(userId);

            if (!user.getIsSuperAdmin() && !isBoardAdmin && !isAuthor) {
                throw new BusinessException(ErrorCode.POST_NOT_FOUND); // Treat as not found for
                // security
            }
        }

        if (incrementView) {
            post.incrementViewCount();

            if (userId != null) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                ViewHistory viewHistory = viewHistoryRepository.findByUserAndPost(user, post)
                        .orElseGet(() -> viewHistoryRepository
                                .save(new ViewHistory(user, post)));
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        if (!board.getIsActive()) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }

        if (request.isNotice()) {
            boolean isBoardAdmin = adminRepository.findByUserAndBoardAndIsActive(user, board, true)
                    .isPresent();

            if (!user.getIsSuperAdmin() && !isBoardAdmin) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }

        BoardCategory category = null;
        if (request.getCategoryId() != null) {
            category = boardCategoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

            // Category Permission Check
            String minRole = category.getMinWriteRole();
            if (Role.SUPER_ADMIN.equals(minRole)) {
                if (!user.getIsSuperAdmin()) {
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                }
            } else if (Role.BOARD_ADMIN.equals(minRole)) {
                boolean isBoardAdmin = adminRepository.findByUserAndBoardAndIsActive(user, board, true)
                        .isPresent();
                if (!user.getIsSuperAdmin() && !isBoardAdmin) {
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                }
            }
        }

        // 본문에서 위험한 스크립트만 제거 (HTML 태그는 허용)
        String sanitizedContents = InputSanitizer.sanitize(request.getContents());

        Post post = Post.builder()
                .board(board)
                .user(user)
                .category(category)
                .title(request.getTitle())
                .contents(sanitizedContents)
                .isNotice(request.isNotice())
                .isNsfw(request.isNsfw())
                .isSpoiler(request.isSpoiler())
                .build();

        Post savedPost = postRepository.save(post);
        tagService.processTagsForPost(savedPost, request.getTags());
        savePostVersion(savedPost, user, "CREATE", null, null);

        if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
            for (Long fileId : request.getFileIds()) {
                fileService.associateFileWithEntity(fileId, savedPost.getPostId(), "POST_CONTENT");
            }
        }

        // 포인트 지급 (게시글 작성)
        String postCreateRewardStr = globalConfigService.getConfig("POINT_POST_CREATE_REWARD");
        int postCreateReward = postCreateRewardStr != null ? Integer.parseInt(postCreateRewardStr) : 50;
        pointService.addPoint(userId, postCreateReward, "게시글 작성", savedPost.getPostId(), "POST");
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
        }

        String originalTitle = post.getTitle();
        String originalContents = post.getContents();

        // 본문에서 위험한 스크립트만 제거 (HTML 태그는 허용)
        String sanitizedContents = InputSanitizer.sanitize(request.getContents());

        post.updatePost(category, request.getTitle(), sanitizedContents, request.isNsfw(),
                request.isSpoiler());
        tagService.processTagsForPost(post, request.getTags());

        if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
            for (Long fileId : request.getFileIds()) {
                fileService.associateFileWithEntity(fileId, post.getPostId(), "POST_CONTENT");
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

        // 포인트 차감 (게시글 삭제)
        String postCreateRewardStr = globalConfigService.getConfig("POINT_POST_CREATE_REWARD");
        int postCreateReward = postCreateRewardStr != null ? Integer.parseInt(postCreateRewardStr) : 50;
        pointService.forceSubtractPoint(userId, postCreateReward, "게시글 삭제", postId, "POST");
    }

    @Transactional
    public int likePost(@NonNull Long userId, @NonNull Long postId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (post.getIsDeleted()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        PostLikeId postLikeId = new PostLikeId(userId, postId);
        if (postLikeRepository.existsById(postLikeId)) {
            throw new BusinessException(ErrorCode.ALREADY_LIKED);
        }

        PostLike postLike = PostLike.builder()
                .user(user)
                .post(post)
                .build();
        postLikeRepository.save(postLike);
        post.incrementLikeCount();

        String content = user.getDisplayName() + "님이 회원님의 게시글을 좋아합니다.";
        NotificationEvent event = new NotificationEvent(post.getUser(), user, "LIKE", "POST", postId, content);
        eventPublisher.publishEvent(event);

        return post.getLikeCount();
    }

    @Transactional
    public int unlikePost(@NonNull Long userId, @NonNull Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (post.getIsDeleted()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        PostLikeId postLikeId = new PostLikeId(userId, postId);
        if (!postLikeRepository.existsById(postLikeId)) {
            throw new BusinessException(ErrorCode.NOT_LIKED);
        }

        postLikeRepository.deleteById(postLikeId);
        post.decrementLikeCount();

        return post.getLikeCount();
    }

    @Transactional
    public void scrapPost(@NonNull Long userId, @NonNull Long postId, String remark) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (post.getIsDeleted()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

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
        // Unscrap은 게시글이 삭제되었어도 내 스크랩 목록에서 삭제 가능해야 할 수 있음.
        // 하지만 일관성을 위해 체크하지 않거나, 체크해도 무방함. 여기서는 게시글 존재 여부 체크 안함 (스크랩 ID로만 삭제)
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
        Board board = boardRepository.findByBoardUrl(request.getBoardUrl()) // boardUrl 사용
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
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

    public List<PostVersionResponse> getPostVersions(@NonNull Long postId) {
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

        List<Long> postIds = historyPage.getContent().stream()
                .map(h -> h.getPost().getPostId())
                .collect(Collectors.toList());

        Set<Long> postIdsWithImages = getPostIdsWithImages(postIds);

        return historyPage.map(viewHistory -> {
            PostSummary summary = PostSummary.from(viewHistory.getPost());
            summary.setHasImage(postIdsWithImages.contains(viewHistory.getPost().getPostId()));
            return summary;
        });
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
        if (user.getIsSuperAdmin())
            return true;
        Board board = boardRepository.findById(boardId).orElse(null);
        if (board == null)
            return false;
        return adminRepository.findByUserAndBoardAndIsActive(user, board, true).isPresent();
    }

    public List<PostSummary> getLatestPostsByBoard(Long boardId, int limit, Long currentUserId) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));

        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIds(currentUserId);
        }

        Page<Post> postPage = postRepository.findByBoardIdAndCategoryId(boardId, null, null, blockedUserIds,
                pageable);
        List<Post> posts = postPage.getContent();

        if (posts.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> postIds = posts.stream().map(Post::getPostId).collect(Collectors.toList());
        List<Board> boards = posts.stream().map(Post::getBoard).distinct().collect(Collectors.toList());

        Set<Long> postIdsWithImages = getPostIdsWithImages(postIds);

        // Batch fetch user interactions if logged in
        Set<Long> likedPostIds = new HashSet<>();
        Set<Long> scrappedPostIds = new HashSet<>();
        Set<String> subscribedBoardUrls = new HashSet<>();

        if (currentUserId != null) {
            User user = userRepository.findById(currentUserId).orElse(null);
            if (user != null) {
                likedPostIds = postLikeRepository.findByUserAndPostIn(user, posts).stream()
                        .map(like -> like.getPost().getPostId())
                        .collect(Collectors.toSet());

                scrappedPostIds = scrapRepository.findByUserAndPostIn(user, posts).stream()
                        .map(scrap -> scrap.getPost().getPostId())
                        .collect(Collectors.toSet());

                subscribedBoardUrls = boardSubscriptionRepository.findByUserAndBoardIn(user, boards)
                        .stream()
                        .map(sub -> sub.getBoard().getBoardUrl())
                        .collect(Collectors.toSet());
            }
        }

        final Set<Long> finalLikedPostIds = likedPostIds;
        final Set<Long> finalScrappedPostIds = scrappedPostIds;
        final Set<String> finalSubscribedBoardUrls = subscribedBoardUrls;

        return posts.stream()
                .map(post -> {
                    String summary = post.getContents().replaceAll("<[^>]*>", "").trim();
                    if (summary.length() > 1000) {
                        summary = summary.substring(0, 1000);
                    }
                    return PostSummary.from(
                            post,
                            postIdsWithImages.contains(post.getPostId()) ? "/api/v1/files/"
                                    + fileService.getOneImageFileIdForPost(
                                            post.getPostId())
                                    : null,
                            post.getBoard().getIconUrl(),
                            finalLikedPostIds.contains(post.getPostId()),
                            finalScrappedPostIds.contains(post.getPostId()),
                            finalSubscribedBoardUrls
                                    .contains(post.getBoard().getBoardUrl()),
                            postIdsWithImages.contains(post.getPostId()),
                            summary);
                })
                .collect(Collectors.toList());
    }
}
