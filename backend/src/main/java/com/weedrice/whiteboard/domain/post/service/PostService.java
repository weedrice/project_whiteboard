package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.domain.post.dto.PostDraftRequest;
import com.weedrice.whiteboard.domain.post.dto.PostUpdateRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
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

    public Page<Post> getPosts(Long boardId, Long categoryId, Pageable pageable) {
        return postRepository.findByBoardIdAndCategoryId(boardId, categoryId, pageable);
    }

    public Page<Post> getPostsByTag(Long tagId, Pageable pageable) {
        return postRepository.findByTagId(tagId, pageable);
    }

    public Page<Post> getMyPosts(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return postRepository.findByUserAndIsDeletedOrderByCreatedAtDesc(user, "N", pageable);
    }

    @Transactional
    public Post getPostById(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

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

    public ViewHistory getViewHistory(Long userId, Long postId) {
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
    public Post createPost(Long userId, Long boardId, PostCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
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
                .isNsfw(request.isNsfw())
                .isSpoiler(request.isSpoiler())
                .build();

        Post savedPost = postRepository.save(post);
        tagService.processTagsForPost(savedPost, request.getTags());
        savePostVersion(savedPost, user, "CREATE", null, null);
        return savedPost;
    }

    @Transactional
    public Post updatePost(Long userId, Long postId, PostUpdateRequest request) {
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

        post.updatePost(category, request.getTitle(), request.getContents(), request.isNsfw(), request.isSpoiler());
        tagService.processTagsForPost(post, request.getTags());
        User modifier = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        savePostVersion(post, modifier, "MODIFY", originalTitle, originalContents);

        return post;
    }

    @Transactional
    public void deletePost(Long userId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (!post.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        post.deletePost();
        postTagRepository.findByPost(post).forEach(postTag -> postTag.getTag().decrementPostCount());
        postTagRepository.deleteByPost(post);
        User modifier = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        savePostVersion(post, modifier, "DELETE", post.getTitle(), post.getContents());
    }

    @Transactional
    public void likePost(Long userId, Long postId) {
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
    public void unlikePost(Long userId, Long postId) {
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
    public boolean togglePostScrap(Long userId, Long postId, String remark) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        ScrapId scrapId = new ScrapId(userId, postId);
        if (scrapRepository.existsById(scrapId)) {
            scrapRepository.deleteById(scrapId);
            return false;
        } else {
            Scrap scrap = Scrap.builder()
                    .user(user)
                    .post(post)
                    .remark(remark)
                    .build();
            scrapRepository.save(scrap);
            return true;
        }
    }

    public Page<Scrap> getMyScraps(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return scrapRepository.findByUserOrderByCreatedAtDesc(user, pageable);
    }

    public Page<DraftPost> getDraftPosts(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return draftPostRepository.findByUserOrderByModifiedAtDesc(user, pageable);
    }

    public DraftPost getDraftPost(Long userId, Long draftId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return draftPostRepository.findByDraftIdAndUser(draftId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional
    public DraftPost saveDraftPost(Long userId, PostDraftRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Board board = boardRepository.findById(request.getBoardId())
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
    public void deleteDraftPost(Long userId, Long draftId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        DraftPost draftPost = draftPostRepository.findByDraftIdAndUser(draftId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        draftPostRepository.delete(draftPost);
    }

    private void savePostVersion(Post post, User modifier, String versionType, String originalTitle, String originalContents) {
        PostVersion postVersion = PostVersion.builder()
                .post(post)
                .modifier(modifier)
                .versionType(versionType)
                .originalTitle(originalTitle)
                .originalContents(originalContents)
                .build();
        postVersionRepository.save(postVersion);
    }

    public List<PostVersion> getPostVersions(Long postId) {
        return postVersionRepository.findByPost_PostIdOrderByCreatedAtDesc(postId);
    }

    public List<String> getTagsForPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        return postTagRepository.findByPost(post).stream()
                .map(postTag -> postTag.getTag().getTagName())
                .collect(Collectors.toList());
    }
}
