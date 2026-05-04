package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
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
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    private final ViewHistoryCommandService viewHistoryCommandService;
    private final TagAssignmentService tagAssignmentService;
    private final PostImageAttachmentReader postImageAttachmentReader;
    private final UserBlockService userBlockService;
    private final PostAccessPolicy postAccessPolicy;
    private final BoardAccessPolicy boardAccessPolicy;

    @Transactional
    public PostResponse getPostResponse(@NonNull Long postId, Long userId, boolean incrementView) {
        return getPostResponse(postId, userId, incrementView, DEFAULT_BOARD_PAGE_SIZE);
    }

    @Transactional
    public PostResponse getPostResponse(@NonNull Long postId, Long userId, boolean incrementView, int boardListPageSize) {
        int normalizedBoardListPageSize = PageRequestUtils.of(0, boardListPageSize).getPageSize();
        PostDetailContext context = loadPostDetailContext(postId, userId, incrementView);
        Post post = context.post();
        List<String> tags = getTagsForPost(post);
        boolean isLiked = isPostLikedByUser(postId, userId);
        boolean isScrapped = isPostScrappedByUser(postId, userId);
        List<String> imageUrls = getPostImageUrls(postId);
        boolean isAdmin = isBoardAdmin(context);
        int boardListPage = resolveDefaultBoardListPage(context, normalizedBoardListPageSize);
        Integer viewCount = incrementView ? postRepository.findViewCountByPostId(postId) : null;

        return PostResponse.from(
                post, tags, context.viewHistory(), isLiked, isScrapped, imageUrls, isAdmin, boardListPage, viewCount);
    }

    private PostDetailContext loadPostDetailContext(@NonNull Long postId, Long userId, boolean incrementView) {
        User viewer = getViewer(userId);
        List<Long> blockedUserIds = getBlockedUserIds(viewer);
        Post post = getReadablePost(postId, viewer, blockedUserIds);
        ViewHistory viewHistory = null;

        if (incrementView) {
            postRepository.incrementViewCount(postId);

            if (viewer != null) {
                viewHistory = viewHistoryCommandService.getOrCreate(viewer, post);
                viewHistory.updateView(null, 0);
            }
        } else if (viewer != null) {
            viewHistory = viewHistoryRepository.findByUserAndPost(viewer, post).orElse(null);
        }

        return new PostDetailContext(post, viewer, blockedUserIds, viewHistory);
    }

    private List<String> getTagsForPost(Post post) {
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

    private List<String> getPostImageUrls(@NonNull Long postId) {
        return postImageAttachmentReader.getImageUrls(postId);
    }

    private boolean isBoardAdmin(PostDetailContext context) {
        return boardAccessPolicy.hasBoardAdminAccess(context.post().getBoard(), context.viewer());
    }

    private int resolveDefaultBoardListPage(PostDetailContext context, int boardListPageSize) {
        Post post = context.post();
        Long viewerUserId = context.viewer() != null ? context.viewer().getUserId() : null;
        boolean includeSecret = boardAccessPolicy.canViewSecretPosts(post.getBoard(), context.viewer());

        long postsBefore = postRepository.countPostsBeforeInBoardDefaultOrder(
                post.getBoard().getBoardId(),
                post.getCreatedAt(),
                post.getPostId(),
                context.blockedUserIds(),
                includeSecret,
                viewerUserId);
        long page = postsBefore / boardListPageSize;
        return (int) Math.min(page, Integer.MAX_VALUE);
    }

    private User getViewer(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Post getReadablePost(@NonNull Long postId, User viewer, List<Long> blockedUserIds) {
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        validateReadable(post, viewer, blockedUserIds);
        return post;
    }

    private void validateReadable(Post post, User viewer, List<Long> blockedUserIds) {
        boolean authorBlocked = blockedUserIds != null && blockedUserIds.contains(post.getUser().getUserId());
        postAccessPolicy.validateReadable(post, viewer, authorBlocked);
    }

    private List<Long> getBlockedUserIds(User viewer) {
        if (viewer == null) {
            return null;
        }
        return userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(
                viewer.getUserId());
    }

    private record PostDetailContext(Post post, User viewer, List<Long> blockedUserIds, ViewHistory viewHistory) {
    }
}
