package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.post.dto.PostResponse;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.dto.PostVersionResponse;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.repository.PostVersionRepository;
import com.weedrice.whiteboard.domain.tag.service.TagAssignmentService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostFacadeReadService {

    private final PostRepository postRepository;
    private final PostVersionRepository postVersionRepository;
    private final TagAssignmentService tagAssignmentService;
    private final PostImageAttachmentReader postImageAttachmentReader;
    private final PostReadContextResolver postReadContextResolver;
    private final PostSummaryAssembler postSummaryAssembler;
    private final PostAccessPolicy postAccessPolicy;
    private final BoardAccessPolicy boardAccessPolicy;

    public PostResponse getInquiryPostResponseForAdmin(@NonNull Long postId) {
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!boardAccessPolicy.isInquiryBoard(post.getBoard())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        List<String> tags = tagAssignmentService.getTagNames(post);
        List<String> imageUrls = getPostImageUrls(postId);
        return PostResponse.from(post, tags, null, false, false, imageUrls, true);
    }

    public List<PostVersionResponse> getPostVersions(@NonNull Long postId, @NonNull Long userId) {
        PostReadContext context = postReadContextResolver.resolveForExistingUser(userId);
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        context = postReadContextResolver.withAdminBoardIdsForPosts(context, List.of(post));
        validateReadable(post, context);

        boolean isAuthor = post.getUser().getUserId().equals(userId);
        if (!isAuthor && !hasBoardAdminAccess(post, context)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return postVersionRepository.findVersionResponsesByPostId(postId);
    }

    public List<String> getTagsForPost(@NonNull Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        return tagAssignmentService.getTagNames(post);
    }

    public List<String> getPostImageUrls(@NonNull Long postId) {
        return postImageAttachmentReader.getImageUrls(postId);
    }

    public Set<Long> getPostIdsWithImages(List<Long> postIds) {
        return postImageAttachmentReader.getPostIdsWithImages(postIds);
    }

    private boolean hasBoardAdminAccess(Post post, PostReadContext context) {
        if (boardAccessPolicy.hasBoardAdminAccess(post.getBoard(), context.viewer(), context.activeAdminBoardIds())) {
            return true;
        }
        return boardAccessPolicy.hasBoardAdminAccess(post.getBoard(), context.viewer());
    }

    public Map<Long, PostSummary> getPostSummariesByIds(List<Long> postIds, Long currentUserId) {
        return getPostSummariesByIds(
                postIds,
                posts -> postReadContextResolver.resolveForExistingUserPosts(currentUserId, posts),
                currentUserId,
                false);
    }

    public Map<Long, PostSummary> getPostSummariesByIds(List<Long> postIds, PostSummaryReadContext readContext) {
        Long currentUserId = readContext == null ? null : readContext.currentUserId();
        boolean existingUser = readContext != null && readContext.viewer() != null;
        return getPostSummariesByIds(
                postIds,
                posts -> PostReadContext.from(readContext),
                currentUserId,
                existingUser);
    }

    private Map<Long, PostSummary> getPostSummariesByIds(
            List<Long> postIds,
            Function<List<Post>, PostReadContext> contextResolver,
            Long currentUserId,
            boolean existingUser) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> distinctPostIds = postIds.stream().distinct().toList();
        List<Post> posts = postRepository.findByPostIdInAndIsDeletedFalse(distinctPostIds);
        PostReadContext context = contextResolver.apply(posts);

        Map<Long, Post> postsById = posts.stream()
                .filter(post -> canReadPostSummary(post, context))
                .collect(Collectors.toMap(Post::getPostId, post -> post));

        List<Post> orderedPosts = postIds.stream()
                .map(postsById::get)
                .filter(Objects::nonNull)
                .toList();
        if (orderedPosts.isEmpty()) {
            return Collections.emptyMap();
        }

        List<PostSummary> summaries = existingUser
                ? postSummaryAssembler.assembleLatestPostsForExistingUser(orderedPosts, currentUserId)
                : postSummaryAssembler.assembleLatestPosts(orderedPosts, currentUserId);
        return summaries.stream()
                .collect(Collectors.toMap(PostSummary::getPostId, summary -> summary, (left, right) -> left,
                        LinkedHashMap::new));
    }

    private void validateReadable(Post post, PostReadContext context) {
        postAccessPolicy.validateReadable(
                post,
                context.viewer(),
                context.isAuthorBlocked(post),
                context.activeAdminBoardIds());
    }

    private boolean canReadPostSummary(Post post, PostReadContext context) {
        try {
            validateReadable(post, context);
            return true;
        } catch (BusinessException ex) {
            if (ErrorCode.POST_NOT_FOUND.equals(ex.getErrorCode())) {
                return false;
            }
            throw ex;
        }
    }
}
