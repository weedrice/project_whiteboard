package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentOwnershipService;
import com.weedrice.whiteboard.domain.badge.service.BadgeEvaluationService;
import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.post.constant.ScrapConstraints;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.dto.ScrapFolderRequest;
import com.weedrice.whiteboard.domain.post.dto.ScrapFolderResponse;
import com.weedrice.whiteboard.domain.post.dto.ScrapListResponse;
import com.weedrice.whiteboard.domain.post.dto.ViewHistoryRequest;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.PostLike;
import com.weedrice.whiteboard.domain.post.entity.PostLikeId;
import com.weedrice.whiteboard.domain.post.entity.Scrap;
import com.weedrice.whiteboard.domain.post.entity.ScrapFolder;
import com.weedrice.whiteboard.domain.post.entity.ScrapId;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import com.weedrice.whiteboard.domain.post.repository.PostLikeRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.repository.ScrapRepository;
import com.weedrice.whiteboard.domain.post.repository.ScrapFolderRepository;
import com.weedrice.whiteboard.domain.post.repository.ViewHistoryRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.common.service.ReactionWriter;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.common.util.TextInputNormalizer;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostInteractionService {

    private static final int DEFAULT_SCRAP_PAGE_SIZE = 20;
    private static final Sort DEFAULT_SCRAP_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("post.postId"));
    private static final int DEFAULT_RECENTLY_VIEWED_PAGE_SIZE = 20;
    // Native query pageable sorting is appended as SQL, so use column names here.
    private static final Sort DEFAULT_RECENTLY_VIEWED_SORT = Sort.by(
            Sort.Order.desc("modified_at"),
            Sort.Order.desc("post_id"));

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final ScrapRepository scrapRepository;
    private final ScrapFolderRepository scrapFolderRepository;
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
    private final PostViewCountWriter postViewCountWriter;
    private final EntityManager entityManager;
    private final BadgeEvaluationService badgeEvaluationService;
    private final SanctionService sanctionService;

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
            postViewCountWriter.incrementReadablePostViewCount(postId);
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
        PostReadContext context = postReadContextResolver.resolveForExistingUser(userId);
        User user = context.viewer();
        Post post = getReadablePost(postId, context);
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
        postViewCountWriter.incrementReadablePostViewCount(post.getPostId());
    }

    @Transactional
    public int likePost(@NonNull Long userId, @NonNull Long postId) {
        return likePost(userId, null, postId);
    }

    @Transactional
    public int likePost(@NonNull Long userId, Long actorAgentId, @NonNull Long postId) {
        User user = userWritableResolver.resolveForUpdate(userId);
        sanctionService.validateNotMuted(user);
        Agent actorAgent = agentOwnershipService.resolveOwnedActiveAgent(userId, actorAgentId);
        Post post = getReadablePostForResolvedUser(postId, user);
        return likeResolvedPost(user, actorAgent, post);
    }

    @Transactional
    int likePost(@NonNull Long userId, Agent actorAgent, @NonNull Post post) {
        User user = userWritableResolver.resolveForUpdate(userId);
        sanctionService.validateNotMuted(user);
        validateResolvedActorAgent(userId, actorAgent);
        return likeResolvedPost(user, actorAgent, post);
    }

    private int likeResolvedPost(User user, Agent actorAgent, Post post) {
        User postOwner = resolvePostOwner(post);
        Long postId = post.getPostId();

        PostLike postLike = PostLike.builder()
                .user(user)
                .post(post)
                .build();
        reactionWriter.insertOrThrowDuplicate(
                () -> postLikeRepository.saveAndFlush(postLike),
                ErrorCode.ALREADY_LIKED);

        incrementPostLikeCount(postId);
        int likeCount = getPostLikeCount(postId);

        NotificationEvent event = NotificationEvent.localized(postOwner, user, actorAgent, NotificationType.LIKE,
                NotificationSourceType.POST, postId, "notification.post.liked",
                resolveNotificationActorName(user, actorAgent));
        eventPublisher.publishEvent(event);
        badgeEvaluationService.evaluatePopularPostBadges(postOwner.getUserId(), likeCount);

        return likeCount;
    }

    private void validateResolvedActorAgent(Long userId, Agent actorAgent) {
        if (actorAgent == null) {
            return;
        }
        if (actorAgent.getUser() == null || !Objects.equals(actorAgent.getUser().getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    @Transactional
    public int unlikePost(@NonNull Long userId, @NonNull Long postId) {
        User user = userWritableResolver.resolveForUpdate(userId);
        sanctionService.validateNotMuted(user);
        getReadablePostForResolvedUser(postId, user);

        int deletedCount = postLikeRepository.deleteByUserIdAndPostId(userId, postId);
        if (deletedCount == 0) {
            throw new BusinessException(ErrorCode.NOT_LIKED);
        }

        decrementPostLikeCount(postId);
        return getPostLikeCount(postId);
    }

    @Transactional
    public void scrapPost(@NonNull Long userId, @NonNull Long postId, String remark) {
        String normalizedRemark = normalizeScrapRemark(remark);
        User user = userWritableResolver.resolveForUpdate(userId);
        Post post = getReadablePostForResolvedUser(postId, user);

        Scrap scrap = Scrap.builder()
                .user(user)
                .post(post)
                .remark(normalizedRemark)
                .build();
        reactionWriter.insertOrThrowDuplicate(
                () -> scrapRepository.saveAndFlush(scrap),
                ErrorCode.ALREADY_SCRAPED);
    }

    @Transactional
    public void unscrapPost(@NonNull Long userId, @NonNull Long postId) {
        User user = userWritableResolver.resolveForUpdate(userId);
        getReadablePostForResolvedUser(postId, user);
        long deletedCount = scrapRepository.deleteByUser_UserIdAndPost_PostId(userId, postId);
        if (deletedCount == 0) {
            throw new BusinessException(ErrorCode.NOT_SCRAPED);
        }
    }

    public ScrapListResponse getMyScraps(@NonNull Long userId, Long folderId, String keyword,
            @NonNull Pageable pageable) {
        PostReadContext context = postReadContextResolver.resolveForExistingUser(userId);
        User user = context.viewer();
        if (folderId != null) {
            validateScrapFolderOwner(userId, folderId);
        }
        Pageable safePageable = PageRequestUtils.of(pageable, DEFAULT_SCRAP_PAGE_SIZE, DEFAULT_SCRAP_SORT);
        BlockedUserFilter blockedUsers = BlockedUserFilter.from(context.blockedUserIdSet());
        String normalizedKeyword = normalizeScrapSearchKeyword(keyword);
        Page<Scrap> scrapPage;
        if (normalizedKeyword == null) {
            scrapPage = folderId == null
                    ? scrapRepository.findPageByUserWithPostDetails(
                            user,
                            user.isUsableSuperAdmin(),
                            blockedUsers.empty(),
                            blockedUsers.ids(),
                            BoardPolicyConstants.INQUIRY_BOARD_URL,
                            safePageable)
                    : scrapRepository.findPageByUserWithPostDetails(
                            user,
                            folderId,
                            user.isUsableSuperAdmin(),
                            blockedUsers.empty(),
                            blockedUsers.ids(),
                            BoardPolicyConstants.INQUIRY_BOARD_URL,
                            safePageable);
        } else {
            scrapPage = scrapRepository.findPageByUserWithPostDetailsByKeyword(
                    user,
                    folderId,
                    toCaseInsensitiveLikePattern(normalizedKeyword),
                    user.isUsableSuperAdmin(),
                    blockedUsers.empty(),
                    blockedUsers.ids(),
                    BoardPolicyConstants.INQUIRY_BOARD_URL,
                    safePageable);
        }
        return ScrapListResponse.from(scrapPage);
    }

    public ScrapListResponse getMyScraps(@NonNull Long userId, @NonNull Pageable pageable) {
        return getMyScraps(userId, null, null, pageable);
    }

    public List<ScrapFolderResponse> getScrapFolders(@NonNull Long userId) {
        userWritableResolver.resolve(userId);
        return ScrapFolderResponse.listFrom(scrapFolderRepository.findByUser_UserIdOrderBySortOrderAscFolderIdAsc(userId));
    }

    @Transactional
    public ScrapFolderResponse createScrapFolder(@NonNull Long userId, ScrapFolderRequest request) {
        User user = userWritableResolver.resolveForUpdate(userId);
        String name = normalizeScrapFolderName(request != null ? request.getName() : null);
        if (scrapFolderRepository.existsByUser_UserIdAndName(userId, name)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        ScrapFolder folder = ScrapFolder.builder()
                .user(user)
                .name(name)
                .sortOrder(request != null ? request.getSortOrder() : null)
                .build();
        try {
            return ScrapFolderResponse.from(scrapFolderRepository.saveAndFlush(folder));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    @Transactional
    public ScrapFolderResponse updateScrapFolder(@NonNull Long userId, @NonNull Long folderId,
            ScrapFolderRequest request) {
        ScrapFolder folder = getOwnedScrapFolderForUpdate(userId, folderId);
        String name = request == null ? null : TextInputNormalizer.normalizeOptional(request.getName(), 60);
        if (name != null && name.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (name != null && !name.equals(folder.getName())
                && scrapFolderRepository.existsByUser_UserIdAndName(userId, name)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        folder.update(name, request != null ? request.getSortOrder() : null);
        try {
            scrapFolderRepository.flush();
            return ScrapFolderResponse.from(folder);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    @Transactional
    public void deleteScrapFolder(@NonNull Long userId, @NonNull Long folderId) {
        ScrapFolder folder = getOwnedScrapFolderForUpdate(userId, folderId);
        scrapFolderRepository.delete(folder);
    }

    public Page<PostSummary> getRecentlyViewedPosts(@NonNull Long userId, @NonNull Pageable pageable) {
        PostReadContext context = postReadContextResolver.resolveForExistingUser(userId);
        User user = context.viewer();
        Pageable safePageable = PageRequestUtils.of(pageable, DEFAULT_RECENTLY_VIEWED_PAGE_SIZE,
                DEFAULT_RECENTLY_VIEWED_SORT);
        BlockedUserFilter blockedUsers = BlockedUserFilter.from(context.blockedUserIdSet());
        Page<Long> visiblePostIdsPage = viewHistoryRepository.findVisiblePostIdsByUserIdOrderByModifiedAtDesc(
                userId,
                user.isUsableSuperAdmin(),
                blockedUsers.empty(),
                blockedUsers.ids(),
                BoardPolicyConstants.INQUIRY_BOARD_URL,
                safePageable);

        if (visiblePostIdsPage.isEmpty()) {
            return Page.empty(safePageable);
        }

        Map<Long, Post> postsById = postRepository
                .findByPostIdInAndIsDeletedFalseAndIsBlindedFalse(visiblePostIdsPage.getContent())
                .stream()
                .collect(Collectors.toMap(Post::getPostId, post -> post));
        List<Post> orderedPosts = visiblePostIdsPage.getContent().stream()
                .map(postsById::get)
                .filter(Objects::nonNull)
                .toList();
        List<PostSummary> orderedSummaries = postSummaryAssembler.assembleLatestPosts(orderedPosts, userId);

        return new PageImpl<>(orderedSummaries, safePageable, visiblePostIdsPage.getTotalElements());
    }

    private long resolveDurationMs(ViewHistoryRequest request) {
        Long durationMs = request.getDurationMs();
        if (durationMs == null) {
            return 0L;
        }
        if (durationMs < 0 || durationMs > ViewHistoryRequest.MAX_DURATION_MS) {
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
        return commentRepository.findByCommentIdAndPost_PostIdAndIsDeletedFalse(lastReadCommentId, postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE));
    }

    private String normalizeScrapRemark(String remark) {
        return TextInputNormalizer.normalizeOptional(remark, ScrapConstraints.MAX_REMARK_LENGTH);
    }

    private String normalizeScrapFolderName(String name) {
        String normalizedName = TextInputNormalizer.normalizeOptional(name, 60);
        if (normalizedName == null || normalizedName.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedName;
    }

    private String normalizeScrapSearchKeyword(String keyword) {
        return TextInputNormalizer.normalizeOptional(keyword, 255);
    }

    private String toCaseInsensitiveLikePattern(String keyword) {
        String escapedKeyword = keyword.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escapedKeyword + "%";
    }

    private void validateScrapFolderOwner(Long userId, Long folderId) {
        if (scrapFolderRepository.findByFolderIdAndUser_UserId(folderId, userId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    private ScrapFolder getOwnedScrapFolder(Long userId, Long folderId) {
        return scrapFolderRepository.findByFolderIdAndUser_UserId(folderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private ScrapFolder getOwnedScrapFolderForUpdate(Long userId, Long folderId) {
        return scrapFolderRepository.findOwnedByIdForUpdate(folderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private Post getReadablePost(@NonNull Long postId, PostReadContext context) {
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        PostReadContext enrichedContext = postReadContextResolver.withAdminBoardIds(context, List.of(post.getBoard()));
        validateReadable(post, enrichedContext);
        return post;
    }

    private Post getReadablePostForResolvedUser(@NonNull Long postId, User user) {
        return getReadablePost(postId, postReadContextResolver.resolveForResolvedUser(user));
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

    private void incrementPostLikeCount(Long postId) {
        if (postRepository.incrementLikeCount(postId) == 0) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
    }

    private void decrementPostLikeCount(Long postId) {
        if (postRepository.decrementLikeCount(postId) == 0) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
    }

    private User resolvePostOwner(Post post) {
        if (post.getAgent() != null) {
            return post.getAgent().getUser();
        }
        return post.getUser();
    }

    private String resolveNotificationActorName(User user, Agent actorAgent) {
        if (actorAgent != null && actorAgent.getName() != null && !actorAgent.getName().isBlank()) {
            return actorAgent.getName();
        }
        return user.getDisplayName();
    }

    private record BlockedUserFilter(boolean empty, List<Long> ids) {
        private static final List<Long> NO_BLOCKED_USER_IDS = List.of(-1L);

        static BlockedUserFilter from(Set<Long> blockedUserIds) {
            if (blockedUserIds == null || blockedUserIds.isEmpty()) {
                return new BlockedUserFilter(true, NO_BLOCKED_USER_IDS);
            }
            return new BlockedUserFilter(false, new ArrayList<>(blockedUserIds));
        }
    }

}
