package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.post.dto.PostResponse;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.dto.PostVersionResponse;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.PostVersion;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.repository.PostVersionRepository;
import com.weedrice.whiteboard.domain.tag.service.TagAssignmentService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostFacadeReadService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostVersionRepository postVersionRepository;
    private final TagAssignmentService tagAssignmentService;
    private final PostImageAttachmentReader postImageAttachmentReader;
    private final PostReadContextResolver postReadContextResolver;
    private final PostSummaryAssembler postSummaryAssembler;
    private final PostInteractionService postInteractionService;
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
        User viewer = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Post post = postInteractionService.getPostById(postId, userId, false);

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
        return tagAssignmentService.getTagNames(post);
    }

    public List<String> getPostImageUrls(@NonNull Long postId) {
        return postImageAttachmentReader.getImageUrls(postId);
    }

    public Set<Long> getPostIdsWithImages(List<Long> postIds) {
        return postImageAttachmentReader.getPostIdsWithImages(postIds);
    }

    public Map<Long, PostSummary> getPostSummariesByIds(List<Long> postIds, Long currentUserId) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> distinctPostIds = postIds.stream().distinct().toList();
        List<Post> posts = postRepository.findByPostIdInAndIsDeletedFalse(distinctPostIds);
        PostReadContext context = postReadContextResolver.resolveForExistingUserPosts(currentUserId, posts);

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

        return postSummaryAssembler.assembleLatestPosts(orderedPosts, currentUserId).stream()
                .collect(Collectors.toMap(PostSummary::getPostId, summary -> summary, (left, right) -> left,
                        LinkedHashMap::new));
    }

    private boolean canReadPostSummary(Post post, PostReadContext context) {
        try {
            postAccessPolicy.validateReadable(
                    post,
                    context.viewer(),
                    context.isAuthorBlocked(post),
                    context.activeAdminBoardIds());
            return true;
        } catch (BusinessException ex) {
            if (ErrorCode.POST_NOT_FOUND.equals(ex.getErrorCode())) {
                return false;
            }
            throw ex;
        }
    }
}
