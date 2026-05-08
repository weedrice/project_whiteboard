package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentOwnershipService;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.dto.ScrapListResponse;
import com.weedrice.whiteboard.domain.post.dto.ViewHistoryRequest;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.PostLike;
import com.weedrice.whiteboard.domain.post.entity.PostLikeId;
import com.weedrice.whiteboard.domain.post.entity.Scrap;
import com.weedrice.whiteboard.domain.post.entity.ScrapId;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import com.weedrice.whiteboard.domain.post.repository.PostLikeRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.repository.ScrapRepository;
import com.weedrice.whiteboard.domain.post.repository.ViewHistoryRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.common.service.ReactionWriter;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostInteractionService {

    private static final long MAX_VIEW_DURATION_MS = 86_400_000L;
    private static final int DEFAULT_SCRAP_PAGE_SIZE = 20;
    private static final Sort DEFAULT_SCRAP_SORT = Sort.by(Sort.Order.desc("createdAt"));
    private static final Set<String> ALLOWED_SCRAP_SORTS = Set.of("createdAt");

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final ScrapRepository scrapRepository;
    private final ViewHistoryRepository viewHistoryRepository;
    private final ViewHistoryCommandService viewHistoryCommandService;
    private final CommentRepository commentRepository;
    private final PostReadContextResolver postReadContextResolver;
    private final AgentOwnershipService agentOwnershipService;
    private final UserWritableResolver userWritableResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final PostSummaryAssembler postSummaryAssembler;
    private final PostAccessPolicy postAccessPolicy;
    private final ReactionWriter reactionWriter;
    private final EntityManager entityManager;

    @Transactional
    public Post getPostById(@NonNull Long postId, Long userId) {
        return getPostById(postId, userId, true);
    }

    @Transactional
    public Post getPostById(@NonNull Long postId, Long userId, boolean incrementView) {
        PostReadContext context = postReadContextResolver.resolveForExistingUser(userId);
        User viewer = context.viewer();
        Post post = getReadablePost(postId, context);

        if (incrementView) {
            postRepository.incrementViewCount(postId);
            entityManager.refresh(post);

            if (viewer != null) {
                viewHistoryCommandService.touchView(viewer, post);
            }
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
        User user = postReadContextResolver.resolveForExistingUser(userId).viewer();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        return viewHistoryRepository.findByUserAndPost(user, post).orElse(null);
    }

    @Transactional
    public void updateViewHistory(@NonNull Long userId, @NonNull Long postId, ViewHistoryRequest request) {
        PostReadContext context = postReadContextResolver.resolveForExistingUser(userId);
        User user = context.viewer();
        Post post = getReadablePost(postId, context);
        long durationMs = resolveDurationMs(request);
        Comment lastReadComment = resolveLastReadComment(postId, request.getLastReadCommentId());
        ViewHistory viewHistory = viewHistoryCommandService.getOrCreateForUpdate(user, post);
        validateDurationAccumulation(viewHistory.getDurationMs(), durationMs);
        viewHistory.updateView(lastReadComment, durationMs);
    }

    @Transactional
    public void incrementViewCount(@NonNull Long postId) {
        incrementViewCount(postId, null);
    }

    @Transactional
    public void incrementViewCount(@NonNull Long postId, Long userId) {
        Post post = getReadablePost(postId, postReadContextResolver.resolveForExistingUser(userId));
        postRepository.incrementViewCount(post.getPostId());
    }

    @Transactional
    public int likePost(@NonNull Long userId, @NonNull Long postId) {
        return likePost(userId, null, postId);
    }

    @Transactional
    public int likePost(@NonNull Long userId, Long actorAgentId, @NonNull Long postId) {
        User user = userWritableResolver.resolve(userId);
        Agent actorAgent = agentOwnershipService.resolveOwnedActiveAgent(userId, actorAgentId);
        Post post = getPostById(postId, userId, false);
        boolean skipNotification = post.getAgent() != null;
        User postOwner = post.getUser();

        PostLike postLike = PostLike.builder()
                .user(user)
                .post(post)
                .build();
        reactionWriter.insertOrThrowDuplicate(
                () -> postLikeRepository.saveAndFlush(postLike),
                ErrorCode.ALREADY_LIKED);

        postRepository.incrementLikeCount(postId);
        int likeCount = getPostLikeCount(postId);
        if (skipNotification) {
            return likeCount;
        }

        String content = resolveNotificationActorName(user, actorAgent)
                + "\uB2D8\uC774 \uD68C\uC6D0\uB2D8\uC758 \uAC8C\uC2DC\uAE00\uC744 \uC88B\uC544\uD569\uB2C8\uB2E4.";
        NotificationEvent event = new NotificationEvent(postOwner, user, actorAgent, NotificationType.LIKE, "POST",
                postId, content);
        eventPublisher.publishEvent(event);

        return likeCount;
    }

    @Transactional
    public int unlikePost(@NonNull Long userId, @NonNull Long postId) {
        userWritableResolver.resolve(userId);

        int deletedCount = postLikeRepository.deleteByUserIdAndPostId(userId, postId);
        if (deletedCount == 0) {
            throw new BusinessException(ErrorCode.NOT_LIKED);
        }

        postRepository.decrementLikeCount(postId);
        return getPostLikeCount(postId);
    }

    @Transactional
    public void scrapPost(@NonNull Long userId, @NonNull Long postId, String remark) {
        User user = userWritableResolver.resolve(userId);
        Post post = getPostById(postId, userId, false);

        Scrap scrap = Scrap.builder()
                .user(user)
                .post(post)
                .remark(remark)
                .build();
        reactionWriter.insertOrThrowDuplicate(
                () -> scrapRepository.saveAndFlush(scrap),
                ErrorCode.ALREADY_SCRAPED);
    }

    @Transactional
    public void unscrapPost(@NonNull Long userId, @NonNull Long postId) {
        userWritableResolver.resolve(userId);
        long deletedCount = scrapRepository.deleteByUser_UserIdAndPost_PostId(userId, postId);
        if (deletedCount == 0) {
            throw new BusinessException(ErrorCode.NOT_SCRAPED);
        }
    }

    public ScrapListResponse getMyScraps(@NonNull Long userId, @NonNull Pageable pageable) {
        PostReadContext context = postReadContextResolver.resolveForExistingUser(userId);
        User user = context.viewer();
        Pageable safePageable = PageRequestUtils.of(
                pageable,
                DEFAULT_SCRAP_PAGE_SIZE,
                DEFAULT_SCRAP_SORT,
                ALLOWED_SCRAP_SORTS);
        Set<Long> blockedUserIds = context.blockedUserIdSet();
        List<Long> blockedUserIdParams = blockedUserIds.isEmpty()
                ? List.of(-1L)
                : new ArrayList<>(blockedUserIds);
        Page<Scrap> scrapPage = scrapRepository.findPageByUserWithPostDetails(
                user,
                user.isUsableSuperAdmin(),
                blockedUserIds.isEmpty(),
                blockedUserIdParams,
                safePageable);
        return ScrapListResponse.from(scrapPage);
    }

    public Page<PostSummary> getRecentlyViewedPosts(@NonNull Long userId, @NonNull Pageable pageable) {
        PostReadContext context = postReadContextResolver.resolveForExistingUser(userId);
        User user = context.viewer();
        Set<Long> blockedUserIds = context.blockedUserIdSet();
        List<Long> blockedUserIdParams = blockedUserIds.isEmpty()
                ? List.of(-1L)
                : new ArrayList<>(blockedUserIds);
        Page<Long> visiblePostIdsPage = viewHistoryRepository.findVisiblePostIdsByUserIdOrderByModifiedAtDesc(
                userId,
                user.isUsableSuperAdmin(),
                blockedUserIds.isEmpty(),
                blockedUserIdParams,
                pageable);

        if (visiblePostIdsPage.isEmpty()) {
            return Page.empty(pageable);
        }

        Map<Long, Post> postsById = postRepository.findByPostIdInAndIsDeletedFalse(visiblePostIdsPage.getContent())
                .stream()
                .collect(Collectors.toMap(Post::getPostId, post -> post));
        List<Post> orderedPosts = visiblePostIdsPage.getContent().stream()
                .map(postsById::get)
                .filter(Objects::nonNull)
                .toList();
        List<PostSummary> orderedSummaries = postSummaryAssembler.assembleLatestPosts(orderedPosts, userId);

        return new PageImpl<>(orderedSummaries, pageable, visiblePostIdsPage.getTotalElements());
    }

    private long resolveDurationMs(ViewHistoryRequest request) {
        Long durationMs = request.getDurationMs();
        if (durationMs == null) {
            return 0L;
        }
        if (durationMs < 0 || durationMs > MAX_VIEW_DURATION_MS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return durationMs;
    }

    private void validateDurationAccumulation(long currentDurationMs, long durationMs) {
        if (durationMs > 0 && currentDurationMs > Long.MAX_VALUE - durationMs) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private Comment resolveLastReadComment(Long postId, Long lastReadCommentId) {
        if (lastReadCommentId == null) {
            return null;
        }
        return commentRepository.findByCommentIdAndPost_PostId(lastReadCommentId, postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE));
    }

    private Post getReadablePost(@NonNull Long postId, PostReadContext context) {
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        PostReadContext enrichedContext = postReadContextResolver.withAdminBoardIds(context, List.of(post.getBoard()));
        validateReadable(post, enrichedContext);
        return post;
    }

    private void validateReadable(Post post, PostReadContext context) {
        postAccessPolicy.validateReadable(
                post,
                context.viewer(),
                context.isAuthorBlocked(post),
                context.activeAdminBoardIds());
    }

    private int getPostLikeCount(Long postId) {
        Integer likeCount = postRepository.findLikeCountByPostId(postId);
        if (likeCount == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return likeCount;
    }

    private String resolveNotificationActorName(User user, Agent actorAgent) {
        if (actorAgent != null && actorAgent.getName() != null && !actorAgent.getName().isBlank()) {
            return actorAgent.getName();
        }
        return user.getDisplayName();
    }

}
