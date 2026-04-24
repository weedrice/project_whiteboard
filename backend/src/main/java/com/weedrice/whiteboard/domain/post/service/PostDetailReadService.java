package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.dto.PostResponse;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.PostLikeId;
import com.weedrice.whiteboard.domain.post.entity.ScrapId;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import com.weedrice.whiteboard.domain.post.repository.PostLikeRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.repository.ScrapRepository;
import com.weedrice.whiteboard.domain.post.repository.ViewHistoryRepository;
import com.weedrice.whiteboard.domain.tag.service.TagAssignmentService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostDetailReadService {

    private static final int DEFAULT_BOARD_PAGE_SIZE = 20;

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostLikeRepository postLikeRepository;
    private final ScrapRepository scrapRepository;
    private final ViewHistoryRepository viewHistoryRepository;
    private final TagAssignmentService tagAssignmentService;
    private final FileService fileService;
    private final BoardRepository boardRepository;
    private final UserBlockService userBlockService;
    private final PostAccessPolicy postAccessPolicy;
    private final BoardAccessPolicy boardAccessPolicy;

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

    private Post getPostById(@NonNull Long postId, Long userId, boolean incrementView) {
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

    private List<String> getTagsForPost(@NonNull Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        return tagAssignmentService.getTagNames(post);
    }

    private boolean isPostLikedByUser(@NonNull Long postId, Long userId) {
        if (userId == null) {
            return false;
        }
        return postLikeRepository.existsById(new PostLikeId(userId, postId));
    }

    private boolean isPostScrappedByUser(@NonNull Long postId, Long userId) {
        if (userId == null) {
            return false;
        }
        return scrapRepository.existsById(new ScrapId(userId, postId));
    }

    private ViewHistory getViewHistory(Long userId, @NonNull Long postId) {
        if (userId == null) {
            return null;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        return viewHistoryRepository.findByUserAndPost(user, post).orElse(null);
    }

    private List<String> getPostImageUrls(@NonNull Long postId) {
        return fileService.getFilesByRelatedEntity(postId, "POST_CONTENT").stream()
                .filter(file -> file.getMimeType().startsWith("image/"))
                .map(file -> "/api/v1/files/" + file.getFileId())
                .collect(Collectors.toList());
    }

    private boolean isBoardAdmin(Long userId, Long boardId) {
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

    private boolean canViewSecretPosts(Board board, Long userId) {
        if (userId == null) {
            return false;
        }
        User user = userRepository.findById(userId).orElse(null);
        return boardAccessPolicy.canViewSecretPosts(board, user);
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
}
