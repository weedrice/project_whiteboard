package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.service.FileService; // Import FileService
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.domain.post.dto.PostDraftRequest;
import com.weedrice.whiteboard.domain.post.dto.PostUpdateRequest;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.dto.ViewHistoryRequest;
import com.weedrice.whiteboard.domain.post.entity.*;
import com.weedrice.whiteboard.domain.post.repository.*;
import com.weedrice.whiteboard.domain.tag.repository.PostTagRepository;
import com.weedrice.whiteboard.domain.tag.service.TagService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings({ "null", "unchecked" })
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
        private final com.weedrice.whiteboard.domain.point.service.PointService pointService;
        private final CommentRepository commentRepository;
        private final FileService fileService;
        private final com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository boardSubscriptionRepository;

        // --- boardUrl 기반 public 메서드 (오버로드) ---
        public Page<Post> getPosts(String boardUrl, Long categoryId, Integer minLikes, @NonNull Pageable pageable) {
                Board board = boardRepository.findByBoardUrl(boardUrl)
                                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
                return this.getPosts(board.getBoardId(), categoryId, minLikes, pageable);
        }

        public List<Post> getNotices(String boardUrl) {
                Board board = boardRepository.findByBoardUrl(boardUrl)
                                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
                return this.getNotices(board.getBoardId());
        }

        @Transactional
        public Post createPost(@NonNull Long userId, String boardUrl, PostCreateRequest request) {
                Board board = boardRepository.findByBoardUrl(boardUrl)
                                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
                return this.createPost(userId, board.getBoardId(), request);
        }

        // --- 기존 boardId 기반 public/private 메서스 ---
        public Page<Post> getPosts(Long boardId, Long categoryId, Integer minLikes, @NonNull Pageable pageable) {
                return postRepository.findByBoardIdAndCategoryId(boardId, categoryId, minLikes, pageable);
        }

        public List<Post> getNotices(Long boardId) {
                return postRepository.findByBoard_BoardIdAndIsNoticeAndIsDeletedOrderByCreatedAtDesc(boardId, true,
                                false);
        }

        public Page<Post> getPostsByTag(Long tagId, @NonNull Pageable pageable) {
                return postRepository.findByTagId(tagId, pageable);
        }

        public Page<Post> getMyPosts(@NonNull Long userId, @NonNull Pageable pageable) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                return postRepository.findByUserAndIsDeleted(user, false, pageable);
        }

        // 최신 게시글 15개 조회 (BoardUrl 기반)
        public List<PostSummary> getLatestPostsByBoard(Long boardId, int limit) {
                Board board = boardRepository.findByBoardId(boardId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
                return postRepository.findByBoardAndIsDeletedOrderByCreatedAtDesc(board, false, Pageable.ofSize(limit))
                                .stream()
                                .map(PostSummary::from)
                                .collect(Collectors.toList());
        }

        public List<PostSummary> getTrendingPosts(int limit, Long userId) {
                LocalDateTime since = LocalDateTime.now().minusHours(24);
                List<Post> posts = postRepository.findTrendingPosts(since, Pageable.ofSize(limit));

                if (posts.isEmpty()) {
                        return Collections.emptyList();
                }

                List<Long> postIds = posts.stream().map(Post::getPostId).collect(Collectors.toList());
                List<Board> boards = posts.stream().map(Post::getBoard).distinct().collect(Collectors.toList());

                // Batch fetch files (images)
                List<File> files = fileService
                                .getFilesByRelatedEntityIn(postIds, "POST_CONTENT");
                Map<Long, String> thumbnailMap = files.stream()
                                .filter(f -> f.getMimeType().startsWith("image/"))
                                .collect(Collectors.groupingBy(
                                                File::getRelatedId,
                                                Collectors.collectingAndThen(Collectors.toList(), list -> {
                                                        if (list.isEmpty())
                                                                return null;
                                                        return "/api/v1/files/" + list.getFirst().getFileId();
                                                })));

                // Batch fetch user interactions if logged in
                Set<Long> likedPostIds = new HashSet<>();
                Set<Long> scrappedPostIds = new HashSet<>();
                Set<String> subscribedBoardUrls = new HashSet<>();

                if (userId != null) {
                        User user = userRepository.findById(userId).orElse(null);
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
                                .map(post -> PostSummary.from(
                                                post,
                                                thumbnailMap.get(post.getPostId()),
                                                post.getBoard().getIconUrl(),
                                                finalLikedPostIds.contains(post.getPostId()),
                                                finalScrappedPostIds.contains(post.getPostId()),
                                                finalSubscribedBoardUrls.contains(post.getBoard().getBoardUrl())))
                                .collect(Collectors.toList());
        }

        @Transactional
        public Post getPostById(@NonNull Long postId, Long userId) {
                Post post = postRepository.findById(postId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

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

                post.incrementViewCount();

                if (userId != null) {
                        User user = userRepository.findById(userId)
                                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                        ViewHistory viewHistory = viewHistoryRepository.findByUserAndPost(user, post)
                                        .orElseGet(() -> viewHistoryRepository.save(new ViewHistory(user, post)));
                        viewHistory.updateView(null, 0);
                }

                return post;
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
        public Post createPost(@NonNull Long userId, @NonNull Long boardId, PostCreateRequest request) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                Board board = boardRepository.findById(boardId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

                if (request.isNotice()) {
                        boolean isBoardAdmin = adminRepository.findByUserAndBoardAndIsActive(user, board, true)
                                        .isPresent();

                        if (!user.getIsSuperAdmin() && !isBoardAdmin) {
                                throw new BusinessException(ErrorCode.FORBIDDEN, "공지사항 작성 권한이 없습니다.");
                        }
                }

                BoardCategory category = null;
                if (request.getCategoryId() != null) {
                        category = boardCategoryRepository.findById(request.getCategoryId())
                                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
                }

                Post post = Post.builder()
                                .board(board)
                                .user(user)
                                .category(category)
                                .title(request.getTitle())
                                .contents(request.getContents())
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

                pointService.addPoint(userId, 50, "게시글 작성", savedPost.getPostId(), "POST");
                return savedPost;
        }

        @Transactional
        public Post updatePost(@NonNull Long userId, @NonNull Long postId, PostUpdateRequest request) {
                Post post = postRepository.findById(postId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

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

                post.updatePost(category, request.getTitle(), request.getContents(), request.isNsfw(),
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

                if (!post.getUser().getUserId().equals(userId)) {
                        throw new BusinessException(ErrorCode.FORBIDDEN);
                }

                post.deletePost();
                postTagRepository.findByPost(post).forEach(postTag -> postTag.getTag().decrementPostCount());
                postTagRepository.deleteByPost(post);
                User modifier = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                savePostVersion(post, modifier, "DELETE", post.getTitle(), post.getContents());

                pointService.forceSubtractPoint(userId, 50, "게시글 삭제", postId, "POST");
        }

        @Transactional
        public void likePost(@NonNull Long userId, @NonNull Long postId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                Post post = postRepository.findById(postId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

                PostLikeId postLikeId = new PostLikeId(userId, postId);
                if (postLikeRepository.existsById(postLikeId)) {
                        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "이미 좋아요를 누른 게시글입니다.");
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
        }

        @Transactional
        public void unlikePost(@NonNull Long userId, @NonNull Long postId) {
                Post post = postRepository.findById(postId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

                PostLikeId postLikeId = new PostLikeId(userId, postId);
                if (!postLikeRepository.existsById(postLikeId)) {
                        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "좋아요를 누르지 않은 게시글입니다.");
                }

                postLikeRepository.deleteById(postLikeId);
                post.decrementLikeCount();
        }

        @Transactional
        public void scrapPost(@NonNull Long userId, @NonNull Long postId, String remark) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                Post post = postRepository.findById(postId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

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
                scrapRepository.deleteById(scrapId);
        }

        public Page<Scrap> getMyScraps(@NonNull Long userId, @NonNull Pageable pageable) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                return scrapRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        }

        public Page<DraftPost> getDraftPosts(@NonNull Long userId, @NonNull Pageable pageable) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                return draftPostRepository.findByUserOrderByModifiedAtDesc(user, pageable);
        }

        public DraftPost getDraftPost(@NonNull Long userId, @NonNull Long draftId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                return draftPostRepository.findByDraftIdAndUser(draftId, user)
                                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
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

        public List<PostVersion> getPostVersions(@NonNull Long postId) {
                return postVersionRepository.findByPost_PostIdOrderByCreatedAtDesc(postId);
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
                return viewHistoryRepository.findByUserOrderByModifiedAtDesc(user, pageable)
                                .map(viewHistory -> PostSummary.from(viewHistory.getPost()));
        }

        public List<String> getPostImageUrls(@NonNull Long postId) {
                return fileService.getFilesByRelatedEntity(postId, "POST_CONTENT").stream()
                                .filter(file -> file.getMimeType().startsWith("image/"))
                                .map(file -> "/api/v1/files/" + file.getFileId())
                                .collect(Collectors.toList());
        }
}
