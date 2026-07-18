package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.post.dto.PostResponse;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.repository.PostVersionRepository;
import com.weedrice.whiteboard.domain.tag.service.TagAssignmentService;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostDetailReadService {

    private static final int DEFAULT_BOARD_PAGE_SIZE = 20;
    private static final String MODIFY_VERSION_TYPE = "MODIFY";

    private final PostRepository postRepository;
    private final PostVersionRepository postVersionRepository;
    private final TagAssignmentService tagAssignmentService;
    private final PostImageAttachmentReader postImageAttachmentReader;
    private final PostInteractionContextResolver postInteractionContextResolver;
    private final BoardAccessPolicy boardAccessPolicy;
    private final PostDetailContextResolver postDetailContextResolver;
    private final PollService pollService;
    private final PostSeriesService postSeriesService;

    public PostResponse getPostResponse(@NonNull Long postId, Long userId) {
        return getPostResponse(postId, userId, DEFAULT_BOARD_PAGE_SIZE);
    }

    public PostResponse getPostResponse(@NonNull Long postId, Long userId, int boardListPageSize) {
        return getPostResponse(postId, userId, boardListPageSize, null);
    }

    public PostResponse getPostResponse(
            @NonNull Long postId,
            Long userId,
            int boardListPageSize,
            Integer viewCountOverride) {
        int normalizedBoardListPageSize = PageRequestUtils.of(0, boardListPageSize).getPageSize();
        PostDetailContext context = postDetailContextResolver.resolve(postId, userId);
        return getPostResponse(context, normalizedBoardListPageSize, viewCountOverride);
    }

    PostResponse getPostResponse(
            @NonNull PostDetailContext context,
            int boardListPageSize,
            Integer viewCountOverride) {
        int normalizedBoardListPageSize = PageRequestUtils.of(0, boardListPageSize).getPageSize();
        Post post = context.post();
        List<String> tags = getTagsForPost(post);
        PostUserInteractionContext interactionContext =
                postInteractionContextResolver.resolvePostReactionsForExistingUser(
                        List.of(post),
                        context.readContext().viewerUserId());
        boolean isLiked = interactionContext.likedPostIds().contains(post.getPostId());
        boolean isScrapped = interactionContext.scrappedPostIds().contains(post.getPostId());
        List<String> imageUrls = getPostImageUrls(post.getPostId());
        boolean isAdmin = isBoardAdmin(context);
        int boardListPage = resolveDefaultBoardListPage(context, normalizedBoardListPageSize);

        return PostResponse.from(
                post, tags, context.viewHistory(), isLiked, isScrapped, imageUrls, isAdmin, boardListPage,
                viewCountOverride, pollService.getPollResponse(post.getPostId(), context.readContext().viewerUserId()),
                postSeriesService.getNavigation(post, context.readContext()), resolveEditCount(post.getPostId()));
    }

    private int resolveEditCount(Long postId) {
        return Math.toIntExact(postVersionRepository.countByPostIdAndVersionType(postId, MODIFY_VERSION_TYPE));
    }

    private List<String> getTagsForPost(Post post) {
        return tagAssignmentService.getTagNames(post);
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

}
