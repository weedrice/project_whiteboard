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
    private final PostLikeRepository postLikeRepository;
    private final ScrapRepository scrapRepository;
    private final ViewHistoryRepository viewHistoryRepository;
    private final ViewHistoryCommandService viewHistoryCommandService;
    private final TagAssignmentService tagAssignmentService;
    private final PostImageAttachmentReader postImageAttachmentReader;
    private final PostReadContextResolver postReadContextResolver;
    private final PostAccessPolicy postAccessPolicy;
    private final BoardAccessPolicy boardAccessPolicy;

    public PostResponse getPostResponse(@NonNull Long postId, Long userId) {
        return getPostResponse(postId, userId, DEFAULT_BOARD_PAGE_SIZE);
    }

    public PostResponse getPostResponse(@NonNull Long postId, Long userId, int boardListPageSize) {
        return assemblePostResponse(postId, userId, boardListPageSize, false);
    }

    @Transactional
    public PostResponse getPostResponseWithViewIncrement(
            @NonNull Long postId,
            Long userId,
            int boardListPageSize) {
        return assemblePostResponse(postId, userId, boardListPageSize, true);
    }

    private PostResponse assemblePostResponse(
            @NonNull Long postId,
            Long userId,
            int boardListPageSize,
            boolean incrementView) {
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
        PostReadContext readContext = postReadContextResolver.resolveForExistingUser(userId);
        Post post = findPost(postId);
        readContext = postReadContextResolver.withAdminBoardIds(readContext, List.of(post.getBoard()));
        validateReadable(post, readContext);
        ViewHistory viewHistory = null;

        if (incrementView) {
            postRepository.incrementViewCount(postId);

            if (readContext.viewer() != null) {
                viewHistoryCommandService.touchView(readContext.viewer(), post);
            }
        }

        if (readContext.viewer() != null) {
            viewHistory = viewHistoryRepository.findByUserAndPost(readContext.viewer(), post).orElse(null);
        }

        return new PostDetailContext(post, readContext, viewHistory);
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
        return boardAccessPolicy.hasBoardAdminAccess(
                context.post().getBoard(),
                context.readContext().viewer(),
                context.readContext().activeAdminBoardIds());
    }

    private int resolveDefaultBoardListPage(PostDetailContext context, int boardListPageSize) {
        Post post = context.post();
        Long viewerUserId = context.readContext().viewerUserId();
        boolean includeSecret = context.readContext().canViewSecretPosts(post.getBoard(), boardAccessPolicy);

        long postsBefore = postRepository.countPostsBeforeInBoardDefaultOrder(
                post.getBoard().getBoardId(),
                post.getCreatedAt(),
                post.getPostId(),
                context.readContext().blockedUserIds(),
                includeSecret,
                viewerUserId);
        long page = postsBefore / boardListPageSize;
        return (int) Math.min(page, Integer.MAX_VALUE);
    }

    private Post findPost(@NonNull Long postId) {
        return postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    private void validateReadable(Post post, PostReadContext context) {
        postAccessPolicy.validateReadable(
                post,
                context.viewer(),
                context.isAuthorBlocked(post),
                context.activeAdminBoardIds());
    }

    private record PostDetailContext(Post post, PostReadContext readContext, ViewHistory viewHistory) {
    }
}
