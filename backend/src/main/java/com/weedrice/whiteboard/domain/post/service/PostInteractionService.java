package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.actor.ActorUserPrincipal;
import com.weedrice.whiteboard.domain.actor.ContentActorRef;
import com.weedrice.whiteboard.domain.inquiry.legacy.InquiryLegacyWritePolicy;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.dto.ScrapFolderRequest;
import com.weedrice.whiteboard.domain.post.dto.ScrapFolderResponse;
import com.weedrice.whiteboard.domain.post.dto.ScrapListResponse;
import com.weedrice.whiteboard.domain.post.dto.ViewHistoryRequest;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.port.PostAgentWritePort;
import com.weedrice.whiteboard.domain.post.port.PostUserWritePort;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostInteractionService {

    private final PostRepository postRepository;
    private final PostReactionService postReactionService;
    private final PostScrapService postScrapService;
    private final PostViewHistoryService postViewHistoryService;
    private final PostReadContextResolver postReadContextResolver;
    private final PostAgentWritePort postAgentWritePort;
    private final PostUserWritePort postUserWritePort;
    private final PostAccessPolicy postAccessPolicy;
    private final PostViewCountWriter postViewCountWriter;
    private final EntityManager entityManager;
    private final InquiryLegacyWritePolicy inquiryLegacyWritePolicy;

    @Transactional
    public Post getPostById(@NonNull Long postId, Long userId) {
        return getPostById(postId, userId, true);
    }

    @Transactional
    public Post getPostById(@NonNull Long postId, Long userId, boolean incrementView) {
        PostReadContext context = postReadContextResolver.resolveForExistingUser(userId);
        ActorUserPrincipal viewer = context.viewer();
        Post post = getReadablePost(postId, context);

        if (incrementView) {
            postViewCountWriter.incrementReadablePostViewCount(postId);
            entityManager.refresh(post);

            if (viewer != null) {
                postViewHistoryService.touchView(viewer, post);
            }
        }

        return post;
    }

    public boolean isPostLikedByUser(@NonNull Long postId, Long userId) {
        if (userId == null) {
            return false;
        }
        return postReactionService.isLikedBy(userId, postId);
    }

    public boolean isPostScrappedByUser(@NonNull Long postId, Long userId) {
        return postScrapService.isScrappedBy(userId, postId);
    }

    public ViewHistory getViewHistory(Long userId, @NonNull Long postId) {
        if (userId == null) {
            return null;
        }
        PostReadContext context = postReadContextResolver.resolveForExistingUser(userId);
        ActorUserPrincipal user = context.viewer();
        Post post = getReadablePost(postId, context);
        return postViewHistoryService.get(user, post);
    }

    @Transactional
    public void updateViewHistory(@NonNull Long userId, @NonNull Long postId, ViewHistoryRequest request) {
        PostReadContext context = postReadContextResolver.resolveForExistingUser(userId);
        ActorUserPrincipal user = context.viewer();
        Post post = getReadablePost(postId, context);
        postViewHistoryService.update(user, post, request);
    }

    @Transactional
    public void incrementViewCount(@NonNull Long postId) {
        incrementViewCount(postId, null);
    }

    @Transactional
    public void incrementViewCount(@NonNull Long postId, Long userId) {
        Post post = getReadablePost(postId, postReadContextResolver.resolveForExistingUser(userId));
        postViewCountWriter.incrementReadablePostViewCount(post.getPostId());
    }

    @Transactional
    public int likePost(@NonNull Long userId, @NonNull Long postId) {
        return likePost(userId, null, postId);
    }

    @Transactional
    public int likePost(@NonNull Long userId, Long actorAgentId, @NonNull Long postId) {
        ActorUserPrincipal user = postUserWritePort.validateContentWriteForUpdate(userId);
        Long resolvedAgentId = postAgentWritePort.resolveOwnedActiveAgentId(userId, actorAgentId);
        Post post = getReadablePostForResolvedUser(postId, user);
        inquiryLegacyWritePolicy.requireBoardWritable(post.getBoard());
        return postReactionService.like(new ContentActorRef(userId, resolvedAgentId), post);
    }

    @Transactional
    int likePost(@NonNull Long userId, Long actorAgentId, @NonNull Post post) {
        postUserWritePort.validateContentWriteForUpdate(userId);
        Long resolvedAgentId = postAgentWritePort.resolveOwnedActiveAgentId(userId, actorAgentId);
        inquiryLegacyWritePolicy.requireBoardWritable(post.getBoard());
        return postReactionService.like(new ContentActorRef(userId, resolvedAgentId), post);
    }

    @Transactional
    public int unlikePost(@NonNull Long userId, @NonNull Long postId) {
        ActorUserPrincipal user = postUserWritePort.validateContentWriteForUpdate(userId);
        Post post = getReadablePostForResolvedUser(postId, user);
        inquiryLegacyWritePolicy.requireBoardWritable(post.getBoard());

        return postReactionService.unlike(userId, post);
    }

    @Transactional
    public void scrapPost(@NonNull Long userId, @NonNull Long postId, String remark) {
        scrapPost(userId, postId, remark, null);
    }

    @Transactional
    public void scrapPost(@NonNull Long userId, @NonNull Long postId, String remark, Long folderId) {
        String normalizedRemark = postScrapService.normalizeRemark(remark);
        ActorUserPrincipal user = postUserWritePort.validateForUpdate(userId);
        Post post = getReadablePostForResolvedUser(postId, user);
        postScrapService.scrap(user, post, normalizedRemark, folderId);
    }

    @Transactional
    public void unscrapPost(@NonNull Long userId, @NonNull Long postId) {
        ActorUserPrincipal user = postUserWritePort.validateForUpdate(userId);
        Post post = getReadablePostForResolvedUser(postId, user);
        inquiryLegacyWritePolicy.requireBoardWritable(post.getBoard());
        postScrapService.unscrap(userId, postId);
    }

    @Transactional
    public void moveScrap(@NonNull Long userId, @NonNull Long postId, Long folderId) {
        postUserWritePort.validateForUpdate(userId);
        postScrapService.move(userId, postId, folderId);
    }

    public ScrapListResponse getMyScraps(@NonNull Long userId, Long folderId, String keyword,
            @NonNull Pageable pageable) {
        PostReadContext context = postReadContextResolver.resolveForExistingUser(userId);
        return postScrapService.getMyScraps(userId, context, folderId, keyword, pageable);
    }

    public ScrapListResponse getMyScraps(@NonNull Long userId, @NonNull Pageable pageable) {
        return getMyScraps(userId, null, null, pageable);
    }

    public List<ScrapFolderResponse> getScrapFolders(@NonNull Long userId) {
        postUserWritePort.validate(userId);
        return postScrapService.getFolders(userId);
    }

    @Transactional
    public ScrapFolderResponse createScrapFolder(@NonNull Long userId, ScrapFolderRequest request) {
        ActorUserPrincipal user = postUserWritePort.validateForUpdate(userId);
        return postScrapService.createFolder(user, request);
    }

    @Transactional
    public ScrapFolderResponse updateScrapFolder(@NonNull Long userId, @NonNull Long folderId,
            ScrapFolderRequest request) {
        postUserWritePort.validateForUpdate(userId);
        return postScrapService.updateFolder(userId, folderId, request);
    }

    @Transactional
    public void deleteScrapFolder(@NonNull Long userId, @NonNull Long folderId) {
        postUserWritePort.validateForUpdate(userId);
        postScrapService.deleteFolder(userId, folderId);
    }

    public Page<PostSummary> getRecentlyViewedPosts(@NonNull Long userId, @NonNull Pageable pageable) {
        PostReadContext context = postReadContextResolver.resolveForExistingUser(userId);
        return postViewHistoryService.getRecentlyViewedPosts(userId, context, pageable);
    }

    private Post getReadablePost(@NonNull Long postId, PostReadContext context) {
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        PostReadContext enrichedContext = postReadContextResolver.withAdminBoardIds(context, List.of(post.getBoard()));
        validateReadable(post, enrichedContext);
        return post;
    }

    private Post getReadablePostForResolvedUser(@NonNull Long postId, ActorUserPrincipal user) {
        return getReadablePost(postId, postReadContextResolver.resolveForResolvedUser(user));
    }

    private void validateReadable(Post post, PostReadContext context) {
        postAccessPolicy.validateReadable(
                post,
                context.viewer(),
                context.isAuthorBlocked(post),
                context.activeAdminBoardIds());
    }

}
