package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentBoardItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentBoardListResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentHomeResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentNoteResponses;
import com.weedrice.whiteboard.domain.agent.dto.AgentLimits;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostListItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentProfileResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentRestrictions;
import com.weedrice.whiteboard.domain.agent.dto.AgentStatusResponse;
import com.weedrice.whiteboard.domain.agent.service.AgentPolicyService.AgentDailyStatus;
import com.weedrice.whiteboard.domain.agent.service.AgentPolicyService.AgentPolicySnapshot;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardAiInfo;
import com.weedrice.whiteboard.domain.board.repository.BoardAiInfoRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.service.BlockedUserIdsParameter;
import com.weedrice.whiteboard.domain.comment.service.CommentReadModel;
import com.weedrice.whiteboard.domain.comment.service.CommentReadModelAssembler;
import com.weedrice.whiteboard.domain.comment.service.CommentReadSupport;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int FEED_PAGE_SIZE_LIMIT = 10;
    private static final int HOME_ACTIVITY_LIMIT = 5;
    private static final int HOME_RECENT_POST_LIMIT = 5;
    private static final int HOME_RECOMMENDED_BOARD_LIMIT = 5;
    private static final int HOME_RECENT_FEED_LIMIT = 10;
    private static final int DEFAULT_READ_PAGE_SIZE_LIMIT = 20;
    private static final List<String> WRITE_ENDPOINT_ENFORCEMENTS = List.of(
            "suspension",
            "quota",
            "permission",
            "moderation",
            "validation");
    private static final Sort DEFAULT_POST_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final Sort DEFAULT_AGENT_FEED_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("postId"));
    private static final Sort DEFAULT_COMMENT_SORT = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("commentId"));
    private static final Set<String> ALLOWED_POST_SORT_PROPERTIES = Set.of(
            "createdAt",
            "postId",
            "likeCount",
            "viewCount");

    private final BoardRepository boardRepository;
    private final BoardAiInfoRepository boardAiInfoRepository;
    private final AgentRepository agentRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostService postService;
    private final PostAccessPolicy postAccessPolicy;
    private final UserBlockService userBlockService;
    private final AgentOwnershipService agentOwnershipService;
    private final AgentBoardAccessService agentBoardAccessService;
    private final AgentPostListItemAssembler agentPostListItemAssembler;
    private final CommentReadSupport commentReadSupport;
    private final CommentReadModelAssembler commentReadModelAssembler;
    private final AgentPolicyService agentPolicyService;
    private final AgentNoteService agentNoteService;

    public AgentStatusResponse getStatus(Long agentId) {
        Agent agent = agentOwnershipService.resolveClaimedAgent(agentId);
        AgentPolicySnapshot policy = agentPolicyService.resolve(agent);
        AgentDailyStatus dailyStatus = policy.dailyStatus();


        return AgentStatusResponse.builder()
                .status(agent.getStatus().toLowerCase())
                .name(agent.getName())
                .stats(AgentStatusResponse.Stats.builder()
                        .postsToday(dailyStatus.postsToday())
                        .commentsToday(dailyStatus.commentsToday())
                        .resetAt(dailyStatus.resetAt())
                        .build())
                .limits(policy.limits())
                .restrictions(policy.restrictions())
                .build();
    }

    public AgentProfileResponse getProfile(Long viewerAgentId, String agentName) {
        Agent viewer = agentOwnershipService.resolveActiveAgent(viewerAgentId);
        Agent target = agentRepository.findByNameAndIsDeletedFalse(agentName)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));
        if (target.getUser() == null || target.isPendingClaim()) {
            throw new BusinessException(ErrorCode.AGENT_NOT_FOUND);
        }

        Long targetAgentId = target.getAgentId();
        long postsCount = postRepository.countPublicProfilePostsByAgentId(targetAgentId);
        long commentsCount = commentRepository.countPublicProfileCommentsByAgentId(targetAgentId);
        long likesReceivedCount = postRepository.sumPublicProfilePostLikesByAgentId(targetAgentId)
                + commentRepository.sumPublicProfileCommentLikesByAgentId(targetAgentId);

        List<AgentProfileResponse.RecentPost> recentPosts = postRepository
                .findPublicProfilePostsByAgentId(targetAgentId, PageRequest.of(0, 5))
                .getContent()
                .stream()
                .filter(post -> isAgentReadableProfileActivity(viewer, post.getBoard()))
                .map(this::toProfileRecentPost)
                .toList();
        List<AgentProfileResponse.RecentComment> recentComments = commentRepository
                .findPublicProfileCommentsByAgentId(targetAgentId, PageRequest.of(0, 5))
                .getContent()
                .stream()
                .filter(comment -> isAgentReadableProfileActivity(viewer, comment.getPost().getBoard()))
                .map(this::toProfileRecentComment)
                .toList();
        List<AgentProfileResponse.PrimaryBoard> primaryBoards = postRepository
                .findPrimaryBoardsByAgentPosts(targetAgentId, PageRequest.of(0, 5))
                .stream()
                .map(board -> AgentProfileResponse.PrimaryBoard.builder()
                        .boardId(board.getBoardId())
                        .name(board.getBoardName())
                        .boardUrl(board.getBoardUrl())
                        .build())
                .toList();

        return AgentProfileResponse.builder()
                .agent(AgentProfileResponse.ProfileAgent.builder()
                        .name(target.getName())
                        .displayName(target.getName())
                        .description(target.getDescription())
                        .status(target.getStatus().toLowerCase())
                        .createdAt(toOffsetDateTime(target.getCreatedAt()))
                        .lastActiveAt(toOffsetDateTime(target.getLastUsedAt()))
                        .ownerVerified(Boolean.TRUE.equals(target.getUser().getIsEmailVerified()))
                        .stats(AgentProfileResponse.Stats.builder()
                                .postsCount(postsCount)
                                .commentsCount(commentsCount)
                                .likesReceivedCount(likesReceivedCount)
                                .build())
                        .primaryBoards(primaryBoards)
                        .build())
                .recentPosts(recentPosts)
                .recentComments(recentComments)
                .build();
    }

    public AgentHomeResponse getHome(Long agentId) {
        Agent agent = agentOwnershipService.resolveClaimedAgent(agentId);
        AgentPolicySnapshot policy = agentPolicyService.resolve(agent);
        AgentDailyStatus dailyStatus = policy.dailyStatus();
        boolean activeAgent = agent.isActive();
        if (activeAgent) {
            agentOwnershipService.validateAuthenticatedAgent(agent);
        }

        List<AgentHomeResponse.ActivityOnMyPost> activityOnMyPosts = activeAgent
                ? getActivityOnMyPosts(agentId)
                : List.of();
        List<AgentHomeResponse.MyRecentPost> myRecentPosts = activeAgent
                ? getHomeMyRecentPosts(agent)
                : List.of();
        AgentBoardListResponse writableBoards = activeAgent
                ? getBoards(agent)
                : new AgentBoardListResponse(List.of());
        List<AgentHomeResponse.RecommendedBoard> recommendedBoards = activeAgent
                ? getHomeRecommendedBoards(writableBoards)
                : List.of();
        List<AgentHomeResponse.RecentFeedItem> recentFeed = activeAgent
                ? getHomeRecentFeed(agent)
                : List.of();
        AgentNoteResponses.Summary noteSummary = activeAgent
                ? agentNoteService.getSummary(agentId)
                : AgentNoteResponses.Summary.builder().unreadThreadCount(0).unreadNoteCount(0).build();
        Map<String, AgentHomeResponse.Capability> capabilities = resolveCapabilities(
                policy.limits(),
                policy.restrictions(),
                !writableBoards.getBoards().isEmpty());

        return AgentHomeResponse.builder()
                .agent(AgentHomeResponse.AgentSummary.builder()
                        .status(agent.getStatus().toLowerCase())
                        .name(agent.getName())
                        .newAgent(dailyStatus.postsToday() == 0 && dailyStatus.commentsToday() == 0)
                        .createdAt(toOffsetDateTime(agent.getCreatedAt()))
                        .build())
                .usage(toUsage(dailyStatus, policy.limits()))
                .capabilities(capabilities)
                .noteSummary(noteSummary)
                .hardConstraints(toHardConstraints(policy.limits(), policy.restrictions()))
                .softGuidance(softGuidance())
                .styleGuidance(styleGuidance())
                .activityOnMyPosts(activityOnMyPosts)
                .myRecentPosts(myRecentPosts)
                .recommendedBoards(recommendedBoards)
                .recentFeed(recentFeed)
                .opportunities(resolveOpportunities(capabilities, noteSummary, activityOnMyPosts, myRecentPosts,
                        recentFeed, recommendedBoards))
                .warnings(resolveWarnings(policy.limits(), policy.restrictions()))
                .build();
    }

    public Page<AgentPostListItem> getFeed(Long agentId, Long boardId, Pageable pageable) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        return getFeed(agent, boardId, pageable);
    }

    private Page<AgentPostListItem> getFeed(Agent agent, Long boardId, Pageable pageable) {
        Long agentId = agent.getAgentId();
        Pageable effectivePageable = boundedPageable(
                pageable,
                FEED_PAGE_SIZE_LIMIT,
                DEFAULT_AGENT_FEED_SORT,
                Set.of());
        List<Board> accessibleBoards = agentBoardAccessService.getAccessibleFeedBoards(agent, boardId);
        if (accessibleBoards.isEmpty()) {
            return Page.empty(effectivePageable);
        }

        List<Long> accessibleBoardIds = accessibleBoards.stream()
                .map(Board::getBoardId)
                .toList();
        Set<Long> secretVisibleBoardIds = agentBoardAccessService.resolveBoardAdminIds(
                agent.getUser(), accessibleBoards, accessibleBoardIds);
        List<Long> blockedUserIds = resolveBlockedUserIds(agent.getUser().getUserId());

        Page<Post> posts = postRepository.findAgentFeedByBoardIds(
                accessibleBoardIds,
                blockedUserIds,
                secretVisibleBoardIds,
                agent.getUser().getUserId(),
                effectivePageable);
        return agentPostListItemAssembler.fromPosts(posts, agentId);
    }

    public AgentBoardListResponse getBoards(Long agentId) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        return getBoards(agent);
    }

    private AgentBoardListResponse getBoards(Agent agent) {
        List<Board> agentEnabledBoards =
                boardRepository.findByIsActiveTrueAndIsPublicTrueAndAgentUseYnTrueOrderBySortOrderAscBoardIdAsc();
        if (agentEnabledBoards.isEmpty()) {
            return new AgentBoardListResponse(List.of());
        }

        List<Long> candidateBoardIds = agentEnabledBoards.stream()
                .map(Board::getBoardId)
                .toList();
        Map<Long, List<CategoryResponse>> categoriesByBoardId =
                agentBoardAccessService.loadCategoriesByBoardIds(candidateBoardIds);
        Set<Long> writableBoardIds =
                agentBoardAccessService.resolveWritableBoardIds(agent, agentEnabledBoards, categoriesByBoardId);
        List<Board> writableBoards = agentEnabledBoards.stream()
                .filter(board -> writableBoardIds.contains(board.getBoardId()))
                .toList();
        if (writableBoards.isEmpty()) {
            return new AgentBoardListResponse(List.of());
        }

        List<Long> writableBoardIdsInOrder = writableBoards.stream()
                .map(Board::getBoardId)
                .toList();
        Map<Long, Long> postCountByBoardId = postRepository.countActiveByBoardIds(writableBoardIdsInOrder).stream()
                .collect(Collectors.toMap(
                        PostRepository.BoardPostCountProjection::getBoardId,
                        PostRepository.BoardPostCountProjection::getPostCount));
        Map<Long, String> guidePromptMap = boardAiInfoRepository.findByBoard_BoardIdIn(writableBoardIdsInOrder)
                .stream()
                .collect(Collectors.toMap(BoardAiInfo::getBoardId, BoardAiInfo::getGuidePrompt));

        List<AgentBoardItem> items = writableBoards.stream()
                .map(board -> AgentBoardItem.builder()
                        .boardId(board.getBoardId())
                        .boardName(board.getBoardName())
                        .boardUrl(board.getBoardUrl())
                        .description(board.getDescription())
                        .iconUrl(board.getIconUrl())
                        .guidePrompt(resolveGuidePrompt(board, guidePromptMap.get(board.getBoardId())))
                        .postCount(postCountByBoardId.getOrDefault(board.getBoardId(), 0L))
                        .categories(categoriesByBoardId.getOrDefault(board.getBoardId(), List.of()))
                        .build())
                .toList();

        return new AgentBoardListResponse(items);
    }

    public Page<AgentPostListItem> getMyPosts(Long agentId, Pageable pageable) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        return getMyPosts(agent, pageable);
    }

    private Page<AgentPostListItem> getMyPosts(Agent agent, Pageable pageable) {
        Long agentId = agent.getAgentId();
        Pageable effectivePageable = boundedPageable(
                pageable,
                DEFAULT_READ_PAGE_SIZE_LIMIT,
                DEFAULT_POST_SORT,
                ALLOWED_POST_SORT_PROPERTIES);

        Page<Post> postPage = postRepository.findByAgent_AgentIdAndIsDeleted(agentId, false,
                effectivePageable);
        return agentPostListItemAssembler.fromPosts(postPage, agentId);
    }

    public Page<AgentPostListItem> getBoardPosts(Long agentId, Long boardId, Long categoryId, Pageable pageable) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        Board board = boardRepository.findByBoardId(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        agentBoardAccessService.validateAgentBoardReadable(agent, board);

        boolean includeSecret = agentBoardAccessService.canViewSecretPosts(agent, board);
        Page<Post> postPage = postService.getPosts(
                boardId,
                categoryId,
                null,
                null,
                agent.getUser().getUserId(),
                includeSecret,
                boundedPageable(
                        pageable,
                        DEFAULT_READ_PAGE_SIZE_LIMIT,
                        DEFAULT_POST_SORT,
                        ALLOWED_POST_SORT_PROPERTIES));
        return agentPostListItemAssembler.fromPosts(postPage, agentId);
    }

    public Page<AgentCommentItem> getPostComments(Long agentId, Long postId, Pageable pageable) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        List<Long> blockedUserIdList = resolveBlockedUserIds(agent.getUser().getUserId());
        Set<Long> blockedUserIds = toBlockedUserIdSet(blockedUserIdList);
        validateReadableAgentCommentPost(agent, postId, blockedUserIds);

        Pageable effectivePageable = boundedPageable(
                pageable,
                DEFAULT_READ_PAGE_SIZE_LIMIT,
                DEFAULT_COMMENT_SORT,
                Set.of());
        BlockedUserIdsParameter blockedUserIdsParameter = BlockedUserIdsParameter.from(blockedUserIds);
        Page<Comment> parentComments = commentRepository.findParentsWithChildrenOrNotDeleted(
                postId,
                blockedUserIdsParameter.empty(),
                blockedUserIdsParameter.ids(),
                effectivePageable);
        if (parentComments.isEmpty()) {
            return Page.empty(effectivePageable);
        }

        Map<Long, Long> replyCounts = commentReadSupport.loadVisibleReplyCounts(
                parentComments.getContent(),
                blockedUserIds);
        List<AgentCommentItem> content = parentComments.getContent().stream()
                .map(comment -> toAgentCommentItem(commentReadModelAssembler.from(comment, blockedUserIds, replyCounts)))
                .toList();
        return new PageImpl<>(content, effectivePageable, parentComments.getTotalElements());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(KST).toOffsetDateTime();
    }

    private List<AgentHomeResponse.ActivityOnMyPost> getActivityOnMyPosts(Long agentId) {
        List<CommentRepository.UnreadAgentPostActivityProjection> activities = commentRepository.findUnreadAgentPostActivities(
                agentId,
                PageRequest.of(0, HOME_ACTIVITY_LIMIT));
        if (activities.isEmpty()) {
            return List.of();
        }

        List<AgentHomeResponse.ActivityOnMyPost> items = new ArrayList<>();
        for (CommentRepository.UnreadAgentPostActivityProjection activity : activities) {
            items.add(AgentHomeResponse.ActivityOnMyPost.builder()
                    .postId(activity.getPostId())
                    .title(activity.getPostTitle())
                    .boardId(activity.getBoardId())
                    .boardName(activity.getBoardName())
                    .newCommentCount(activity.getUnreadCount())
                    .latestCommentPreview(toPreview(activity.getLatestCommentContent()))
                    .latestAt(toOffsetDateTime(activity.getLatestCommentCreatedAt()))
                    .lastReadAt(toOffsetDateTime(activity.getLastReadAt()))
                    .build());
        }
        return items;
    }

    private List<AgentHomeResponse.MyRecentPost> getHomeMyRecentPosts(Agent agent) {
        return getMyPosts(agent, PageRequest.of(0, HOME_RECENT_POST_LIMIT))
                .getContent()
                .stream()
                .map(item -> AgentHomeResponse.MyRecentPost.builder()
                        .postId(item.getPostId())
                        .title(item.getTitle())
                        .boardId(item.getBoardId())
                        .boardName(item.getBoardName())
                        .commentCount(item.getCommentCount())
                        .likeCount(item.getLikeCount())
                        .createdAt(item.getCreatedAt())
                        .build())
                .toList();
    }

    private List<AgentHomeResponse.RecommendedBoard> getHomeRecommendedBoards(AgentBoardListResponse writableBoards) {
        return writableBoards.getBoards()
                .stream()
                .filter(board -> hasText(board.getGuidePrompt()) || board.getPostCount() > 0)
                .limit(HOME_RECOMMENDED_BOARD_LIMIT)
                .map(board -> AgentHomeResponse.RecommendedBoard.builder()
                        .boardId(board.getBoardId())
                        .boardUrl(board.getBoardUrl())
                        .name(board.getBoardName())
                        .description(board.getDescription())
                        .guidePrompt(board.getGuidePrompt())
                        .postCount(board.getPostCount())
                        .build())
                .toList();
    }

    private List<AgentHomeResponse.RecentFeedItem> getHomeRecentFeed(Agent agent) {
        return getFeed(agent, null, PageRequest.of(0, HOME_RECENT_FEED_LIMIT))
                .getContent()
                .stream()
                .map(item -> AgentHomeResponse.RecentFeedItem.builder()
                        .postId(item.getPostId())
                        .title(item.getTitle())
                        .contentPreview(toPreview(item.getContent()))
                        .boardId(item.getBoardId())
                        .boardName(item.getBoardName())
                        .commentCount(item.getCommentCount())
                        .likeCount(item.getLikeCount())
                        .createdAt(item.getCreatedAt())
                        .hasMyComment(item.isHasMyComment())
                        .build())
                .toList();
    }

    private AgentHomeResponse.Usage toUsage(AgentDailyStatus dailyStatus, AgentLimits limits) {
        return AgentHomeResponse.Usage.builder()
                .postsToday(dailyStatus.postsToday())
                .commentsToday(dailyStatus.commentsToday())
                .maxPostsPerDay(limits.getMaxPostsPerDay())
                .maxCommentsPerDay(limits.getMaxCommentsPerDay())
                .maxNotesPerDay(limits.getMaxNotesPerDay())
                .postsRemaining(limits.getPostsRemaining())
                .commentsRemaining(limits.getCommentsRemaining())
                .notesRemaining(limits.getNotesRemaining())
                .nextPostAllowedAt(limits.getNextPostAllowedAt())
                .nextCommentAllowedAt(limits.getNextCommentAllowedAt())
                .nextNoteAllowedAt(limits.getNextNoteAllowedAt())
                .resetAt(dailyStatus.resetAt())
                .build();
    }

    private AgentHomeResponse.HardConstraints toHardConstraints(AgentLimits limits, AgentRestrictions restrictions) {
        return AgentHomeResponse.HardConstraints.builder()
                .suspended(restrictions.isSuspended())
                .reason(restrictions.getReason())
                .suspendedUntil(restrictions.getSuspendedUntil())
                .canCreatePost(restrictions.isCanPost())
                .canCreateComment(restrictions.isCanComment())
                .canSendNote(restrictions.isCanSendNote())
                .postsRemaining(limits.getPostsRemaining())
                .commentsRemaining(limits.getCommentsRemaining())
                .notesRemaining(limits.getNotesRemaining())
                .noteDailyLimit(limits.getMaxNotesPerDay())
                .nextPostAllowedAt(limits.getNextPostAllowedAt())
                .nextCommentAllowedAt(limits.getNextCommentAllowedAt())
                .nextNoteAllowedAt(limits.getNextNoteAllowedAt())
                .writeEndpointsEnforce(WRITE_ENDPOINT_ENFORCEMENTS)
                .build();
    }

    private Map<String, AgentHomeResponse.Capability> resolveCapabilities(
            AgentLimits limits,
            AgentRestrictions restrictions,
            boolean hasWritableBoardPermission) {
        Map<String, AgentHomeResponse.Capability> capabilities = new LinkedHashMap<>();
        capabilities.put("create_post", writeCapability(
                restrictions,
                limits.getPostsRemaining(),
                limits.getNextPostAllowedAt(),
                null,
                null,
                null,
                null,
                hasWritableBoardPermission,
                "post_quota_exceeded",
                "no_writable_board_permission"));
        capabilities.put("create_comment", writeCapability(
                restrictions,
                null,
                null,
                limits.getCommentsRemaining(),
                limits.getNextCommentAllowedAt(),
                null,
                null,
                true,
                "comment_quota_exceeded",
                null));
        capabilities.put("create_reply", writeCapability(
                restrictions,
                null,
                null,
                limits.getCommentsRemaining(),
                limits.getNextCommentAllowedAt(),
                null,
                null,
                true,
                "comment_quota_exceeded",
                null));
        capabilities.put("send_note", writeCapability(
                restrictions,
                null,
                null,
                null,
                null,
                limits.getNotesRemaining(),
                limits.getNextNoteAllowedAt(),
                true,
                "note_quota_exceeded",
                null));
        capabilities.put("like_post", simpleCapability(!restrictions.isSuspended(), restrictions));
        capabilities.put("like_comment", simpleCapability(!restrictions.isSuspended(), restrictions));
        capabilities.put("delete_post", simpleCapability(!restrictions.isSuspended(), restrictions));
        capabilities.put("mark_post_activity_read", simpleCapability(!restrictions.isSuspended(), restrictions));
        capabilities.put("get_notes", simpleCapability(!restrictions.isSuspended(), restrictions));
        capabilities.put("get_note_thread", simpleCapability(!restrictions.isSuspended(), restrictions));
        capabilities.put("mark_note_read", simpleCapability(!restrictions.isSuspended(), restrictions));
        capabilities.put("get_feed", simpleCapability(!restrictions.isSuspended(), restrictions));
        capabilities.put("get_board_posts", simpleCapability(!restrictions.isSuspended(), restrictions));
        capabilities.put("get_post_comments", simpleCapability(!restrictions.isSuspended(), restrictions));
        return capabilities;
    }

    private AgentHomeResponse.Capability writeCapability(
            AgentRestrictions restrictions,
            Long postsRemaining,
            OffsetDateTime nextPostAllowedAt,
            Long commentsRemaining,
            OffsetDateTime nextCommentAllowedAt,
            Long notesRemaining,
            OffsetDateTime nextNoteAllowedAt,
            boolean permissionAvailable,
            String quotaReason,
            String permissionReason) {
        List<String> reasons = new ArrayList<>();
        if (restrictions.isSuspended()) {
            reasons.add("suspended");
        }
        if (postsRemaining != null && postsRemaining <= 0) {
            reasons.add(quotaReason);
        }
        if (commentsRemaining != null && commentsRemaining <= 0) {
            reasons.add(quotaReason);
        }
        if (notesRemaining != null && notesRemaining <= 0) {
            reasons.add(quotaReason);
        }
        if (commentsRemaining != null && !restrictions.isCanComment() && !restrictions.isSuspended()
                && commentsRemaining > 0) {
            reasons.add("comment_permission_restricted");
        }
        if (notesRemaining != null && !restrictions.isCanSendNote() && !restrictions.isSuspended()
                && notesRemaining > 0) {
            reasons.add("note_permission_restricted");
        }
        if (!permissionAvailable && permissionReason != null) {
            reasons.add(permissionReason);
        }
        return AgentHomeResponse.Capability.builder()
                .available(reasons.isEmpty())
                .unavailableReasons(reasons)
                .postsRemaining(postsRemaining)
                .commentsRemaining(commentsRemaining)
                .notesRemaining(notesRemaining)
                .nextPostAllowedAt(nextPostAllowedAt)
                .nextCommentAllowedAt(nextCommentAllowedAt)
                .nextNoteAllowedAt(nextNoteAllowedAt)
                .build();
    }

    private AgentHomeResponse.Capability simpleCapability(boolean available, AgentRestrictions restrictions) {
        List<String> reasons = available ? List.of() : List.of("suspended");
        if (restrictions.isSuspended()) {
            available = false;
        }
        return AgentHomeResponse.Capability.builder()
                .available(available)
                .unavailableReasons(reasons)
                .build();
    }

    private List<AgentHomeResponse.Opportunity> resolveOpportunities(
            Map<String, AgentHomeResponse.Capability> capabilities,
            AgentNoteResponses.Summary noteSummary,
            List<AgentHomeResponse.ActivityOnMyPost> activityOnMyPosts,
            List<AgentHomeResponse.MyRecentPost> myRecentPosts,
            List<AgentHomeResponse.RecentFeedItem> recentFeed,
            List<AgentHomeResponse.RecommendedBoard> recommendedBoards) {
        List<AgentHomeResponse.Opportunity> opportunities = new ArrayList<>();
        if (noteSummary != null && noteSummary.getUnreadNoteCount() > 0) {
            opportunities.add(AgentHomeResponse.Opportunity.builder()
                    .type("review_notes")
                    .summary("Unread notes are available for review.")
                    .targetType("notes")
                    .targetId(null)
                    .availableActions(List.of(
                            action("get_notes", Map.of("box", "unread", "page", 0, "size", 20))))
                    .build());
        }
        for (AgentHomeResponse.ActivityOnMyPost activity : activityOnMyPosts) {
            opportunities.add(AgentHomeResponse.Opportunity.builder()
                    .type("reply_to_activity")
                    .summary("Unread activity exists on one of the agent's posts.")
                    .targetType("post")
                    .targetId(activity.getPostId())
                    .availableActions(List.of(
                            action("get_post_comments", Map.of("post_id", activity.getPostId(), "page", 0, "size", 50)),
                            action("mark_post_activity_read", Map.of("post_id", activity.getPostId()))))
                    .build());
        }
        if (!recentFeed.isEmpty()) {
            List<AgentHomeResponse.AvailableAction> actions = new ArrayList<>();
            actions.add(action("get_feed", Map.of("page", 0, "size", HOME_RECENT_FEED_LIMIT)));
            if (isAvailable(capabilities, "create_comment")) {
                actions.add(action("create_comment", Map.of("post_id", recentFeed.get(0).getPostId())));
            }
            opportunities.add(AgentHomeResponse.Opportunity.builder()
                    .type("review_feed")
                    .summary("Recent feed items are available for review.")
                    .targetType("feed")
                    .targetId(null)
                    .availableActions(actions)
                    .build());
        }
        for (AgentHomeResponse.RecommendedBoard board : recommendedBoards) {
            List<AgentHomeResponse.AvailableAction> actions = new ArrayList<>();
            actions.add(action("get_board_posts", Map.of("board_id", board.getBoardId(), "page", 0, "size", 20)));
            if (isAvailable(capabilities, "create_post") && hasText(board.getBoardUrl())) {
                actions.add(action("create_post", Map.of("board_url", board.getBoardUrl())));
            }
            opportunities.add(AgentHomeResponse.Opportunity.builder()
                    .type("explore_board")
                    .summary("A writable board is available.")
                    .targetType("board")
                    .targetId(board.getBoardId())
                    .availableActions(actions)
                    .build());
            if (isAvailable(capabilities, "create_post") && hasText(board.getBoardUrl())) {
                opportunities.add(AgentHomeResponse.Opportunity.builder()
                        .type("create_post_candidate")
                        .summary("The board can accept an agent-authored post candidate.")
                        .targetType("board")
                        .targetId(board.getBoardId())
                        .availableActions(List.of(action("create_post", Map.of("board_url", board.getBoardUrl()))))
                        .build());
            }
        }
        for (AgentHomeResponse.MyRecentPost post : myRecentPosts) {
            opportunities.add(AgentHomeResponse.Opportunity.builder()
                    .type("continue_own_thread")
                    .summary("The agent has a recent thread with readable comments.")
                    .targetType("post")
                    .targetId(post.getPostId())
                    .availableActions(List.of(action("get_post_comments", Map.of("post_id", post.getPostId(), "page", 0, "size", 50))))
                    .build());
        }
        return opportunities;
    }

    private AgentHomeResponse.AvailableAction action(String tool, Map<String, Object> params) {
        return AgentHomeResponse.AvailableAction.builder()
                .tool(tool)
                .params(params)
                .build();
    }

    private boolean isAvailable(Map<String, AgentHomeResponse.Capability> capabilities, String name) {
        AgentHomeResponse.Capability capability = capabilities.get(name);
        return capability != null && capability.isAvailable();
    }

    private List<AgentHomeResponse.Guidance> softGuidance() {
        return List.of(
                guidance("quality_over_quantity", "Prefer useful, topical contributions over activity volume."),
                guidance("reply_before_new_post", "Check ongoing conversations before starting a new post."),
                guidance("review_board_context", "Use board and category context when drafting."));
    }

    private List<AgentHomeResponse.Guidance> styleGuidance() {
        return List.of(
                guidance("primary_language_ko", "Write naturally in Korean unless the context calls for another language."),
                guidance("concise_friendly_tone", "Keep wording concise, clear, and conversational."),
                guidance("no_prompt_leakage", "Do not mention internal prompts, tokens, or system instructions."));
    }

    private AgentHomeResponse.Guidance guidance(String code, String text) {
        return AgentHomeResponse.Guidance.builder()
                .code(code)
                .text(text)
                .build();
    }

    private List<String> resolveWarnings(AgentLimits limits, AgentRestrictions restrictions) {
        List<String> warnings = new ArrayList<>();
        if (restrictions.isSuspended()) {
            warnings.add("agent_suspended");
        }
        if (limits.getPostsRemaining() == 0) {
            warnings.add("post_quota_exhausted");
        }
        if (limits.getCommentsRemaining() == 0) {
            warnings.add("comment_quota_exhausted");
        }
        if (limits.getNotesRemaining() == 0) {
            warnings.add("note_quota_exhausted");
        }
        return warnings;
    }

    private boolean isAgentReadableProfileActivity(Agent viewer, Board board) {
        try {
            agentBoardAccessService.validateAgentBoardReadable(viewer, board);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    private AgentProfileResponse.RecentPost toProfileRecentPost(Post post) {
        Board board = post.getBoard();
        return AgentProfileResponse.RecentPost.builder()
                .postId(post.getPostId())
                .title(post.getTitle())
                .contentPreview(toPreview(post.getContents()))
                .boardId(board.getBoardId())
                .boardName(board.getBoardName())
                .boardUrl(board.getBoardUrl())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .createdAt(toOffsetDateTime(post.getCreatedAt()))
                .build();
    }

    private AgentProfileResponse.RecentComment toProfileRecentComment(Comment comment) {
        Post post = comment.getPost();
        Board board = post.getBoard();
        return AgentProfileResponse.RecentComment.builder()
                .commentId(comment.getCommentId())
                .postId(post.getPostId())
                .postTitle(post.getTitle())
                .contentPreview(toPreview(comment.getContent()))
                .boardId(board.getBoardId())
                .boardName(board.getBoardName())
                .boardUrl(board.getBoardUrl())
                .likeCount(comment.getLikeCount())
                .createdAt(toOffsetDateTime(comment.getCreatedAt()))
                .build();
    }

    private String toPreview(String content) {
        if (content == null) {
            return "";
        }
        String plain = InputSanitizer.stripHtml(content).replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (plain.length() <= 120) {
            return plain;
        }
        return plain.substring(0, 120);
    }

    private Pageable boundedPageable(Pageable pageable, int maxPageSize, Sort defaultSort, Set<String> allowedSortProperties) {
        int pageNumber = pageable != null && pageable.isPaged() ? Math.max(pageable.getPageNumber(), 0) : 0;
        int requestedSize = pageable != null && pageable.isPaged() ? pageable.getPageSize() : maxPageSize;
        int pageSize = Math.min(Math.max(requestedSize, 1), maxPageSize);
        Sort sort = resolveSort(pageable, defaultSort, allowedSortProperties);
        return PageRequest.of(pageNumber, pageSize, sort);
    }

    private Sort resolveSort(Pageable pageable, Sort defaultSort, Set<String> allowedSortProperties) {
        if (pageable == null || !pageable.isPaged() || pageable.getSort().isUnsorted() || allowedSortProperties.isEmpty()) {
            return defaultSort;
        }
        List<Sort.Order> allowedOrders = pageable.getSort().stream()
                .filter(order -> allowedSortProperties.contains(order.getProperty()))
                .toList();
        if (allowedOrders.isEmpty()) {
            return defaultSort;
        }
        return Sort.by(allowedOrders);
    }

    private String resolveGuidePrompt(Board board, String savedGuidePrompt) {
        if (savedGuidePrompt != null) {
            return savedGuidePrompt;
        }
        String description = board.getDescription();
        return description == null || description.isBlank() ? "" : description;
    }

    private List<Long> resolveBlockedUserIds(Long userId) {
        return userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(userId);
    }

    private Set<Long> toBlockedUserIdSet(List<Long> blockedUserIds) {
        return blockedUserIds == null || blockedUserIds.isEmpty()
                ? Set.of()
                : Set.copyOf(blockedUserIds);
    }

    private void validateReadableAgentCommentPost(Agent agent, Long postId, Set<Long> blockedUserIds) {
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        boolean authorBlocked = post.getUser() != null && blockedUserIds.contains(post.getUser().getUserId());
        postAccessPolicy.validateReadable(post, agent.getUser(), authorBlocked);
        agentBoardAccessService.validateAgentBoardReadable(agent, post.getBoard());
    }

    private AgentCommentItem toAgentCommentItem(CommentReadModel model) {
        Comment comment = model.comment();
        return AgentCommentItem.builder()
                .commentId(comment.getCommentId())
                .parentId(comment.getParent() != null ? comment.getParent().getCommentId() : null)
                .postId(comment.getPost().getPostId())
                .content(model.status() == CommentReadModel.Status.ACTIVE ? comment.getContent() : null)
                .depth(comment.getDepth())
                .likeCount(comment.getLikeCount())
                .replyCount(model.replyCount())
                .hasReplies(model.hasReplies())
                .createdAt(comment.getCreatedAt())
                .status(toAgentCommentStatus(model.status()))
                .author(toAgentCommentAuthor(model.author()))
                .build();
    }

    private String toAgentCommentStatus(CommentReadModel.Status status) {
        return switch (status) {
            case ACTIVE -> AgentCommentItem.STATUS_ACTIVE;
            case DELETED -> AgentCommentItem.STATUS_DELETED;
            case BLOCKED_AUTHOR -> AgentCommentItem.STATUS_BLOCKED_AUTHOR;
        };
    }

    private AgentCommentItem.Author toAgentCommentAuthor(CommentReadModel.Author author) {
        if (author == null) {
            return null;
        }
        return AgentCommentItem.Author.builder()
                .userId(author.userId())
                .agentId(author.agentId())
                .authorType(author.authorType())
                .displayName(author.displayName())
                .build();
    }

}
