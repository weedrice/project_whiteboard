package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.domain.post.dto.PostDraftRequest;
import com.weedrice.whiteboard.domain.post.dto.PostUpdateRequest;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.PostLike;
import com.weedrice.whiteboard.domain.post.entity.PostLikeId;
import com.weedrice.whiteboard.domain.post.entity.PostVersion;
import com.weedrice.whiteboard.domain.post.entity.Scrap;
import com.weedrice.whiteboard.domain.post.entity.ScrapId;
import com.weedrice.whiteboard.domain.post.repository.DraftPostRepository;
import com.weedrice.whiteboard.domain.post.repository.PostLikeRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.repository.PostVersionRepository;
import com.weedrice.whiteboard.domain.post.repository.ScrapRepository;
import com.weedrice.whiteboard.domain.tag.entity.PostTag;
import com.weedrice.whiteboard.domain.tag.repository.PostTagRepository;
import com.weedrice.whiteboard.domain.tag.service.TagService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
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

    public Page<Post> getPosts(Long boardId, Long categoryId, Pageable pageable) {
        return postRepository.findByBoardIdAndCategoryId(boardId, categoryId, pageable);
    }

    public Page<Post> getPostsByTag(Long tagId, Pageable pageable) {
        return postRepository.findByTagId(tagId, pageable);
    }

    public Post getPostById(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
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
        tagService.processTagsForPost(savedPost, request.getTags()); // 태그 처리
        savePostVersion(savedPost, user, "CREATE", null, null); // 버전 기록
        return savedPost;
    }

    @Transactional
    public Post updatePost(Long userId, Long postId, PostUpdateRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (!post.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN); // 작성자만 수정 가능
        }

        BoardCategory category = null;
        if (request.getCategoryId() != null) {
            category = boardCategoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        }

        // 이전 내용 저장
        String originalTitle = post.getTitle();
        String originalContents = post.getContents();

        post.updatePost(category, request.getTitle(), request.getContents(), request.isNsfw(), request.isSpoiler());
        tagService.processTagsForPost(post, request.getTags()); // 태그 처리
        User modifier = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        savePostVersion(post, modifier, "MODIFY", originalTitle, originalContents); // 버전 기록

        return post;
    }

    @Transactional
    public void deletePost(Long userId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        // TODO: 관리자 권한 확인 로직 추가 필요
        if (!post.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN); // 작성자만 삭제 가능
        }

        post.deletePost(); // Soft Delete
        // 태그 post_count 감소
        postTagRepository.findByPost(post).forEach(postTag -> postTag.getTag().decrementPostCount());
        postTagRepository.deleteByPost(post); // PostTag 삭제
        User modifier = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        savePostVersion(post, modifier, "DELETE", post.getTitle(), post.getContents()); // 버전 기록
    }

    @Transactional
    public void incrementViewCount(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        post.incrementViewCount();
    }

    @Transactional
    public boolean togglePostLike(Long userId, Long postId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        PostLikeId postLikeId = new PostLikeId(userId, postId);
        if (postLikeRepository.existsById(postLikeId)) {
            postLikeRepository.deleteById(postLikeId);
            post.decrementLikeCount();
            return false; // 좋아요 취소
        } else {
            PostLike postLike = PostLike.builder()
                    .user(user)
                    .post(post)
                    .build();
            postLikeRepository.save(postLike);
            post.incrementLikeCount();
            // TODO: 작성자에게 알림 (notifications INSERT)
            return true; // 좋아요
        }
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
            return false; // 스크랩 취소
        } else {
            Scrap scrap = Scrap.builder()
                    .user(user)
                    .post(post)
                    .remark(remark)
                    .build();
            scrapRepository.save(scrap);
            return true; // 스크랩
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
