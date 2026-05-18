package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentBoardItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentBoardListResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentHomeResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentLimits;
import com.weedrice.whiteboard.domain.agent.dto.AgentNextAction;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostListItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentRestrictions;
import com.weedrice.whiteboard.domain.agent.dto.AgentStatusResponse;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.repository.AgentPostActivityReadRepository;
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
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final Sort DEFAULT_POST_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final Sort DEFAULT_AGENT_FEED_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("postId"));
    private static final Sort DEFAULT_COMMENT_SORT = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("commentId"));
    private static final Set<String> RESTRICTION_TYPES = Set.of("BAN", "MUTE");
    private static final Set<String> ALLOWED_POST_SORT_PROPERTIES = Set.of(
            "createdAt",
            "postId",
            "likeCount",
            "viewCount");

    private final BoardRepository boardRepository;
    private final BoardAiInfoRepository boardAiInfoRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final AgentPostActivityReadRepository agentPostActivityReadRepository;
    private final SanctionRepository sanctionRepository;
    private final PostService postService;
    private final PostAccessPolicy postAccessPolicy;
    private final UserBlockService userBlockService;
    private final AgentOwnershipService agentOwnershipService;
    private final AgentBoardAccessService agentBoardAccessService;
    private final AgentPostListItemAssembler agentPostListItemAssembler;
    private final CommentReadSupport commentReadSupport;
    private final CommentReadModelAssembler commentReadModelAssembler;
    private final AgentQuotaService agentQuotaService;

    public AgentStatusResponse getStatus(Long agentId) {
        Agent agent = agentOwnershipService.resolveClaimedAgent(agentId);
        DailyStatus dailyStatus = resolveDailyStatus(agentId);
        AgentPolicyView policy = resolvePolicy(agent, dailyStatus);


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

    public AgentHomeResponse getHome(Long agentId) {
        Agent agent = agentOwnershipService.resolveClaimedAgent(agentId);
        DailyStatus dailyStatus = resolveDailyStatus(agentId);
        AgentPolicyView policy = resolvePolicy(agent, dailyStatus);

        List<AgentHomeResponse.ActivityOnMyPost> activityOnMyPosts = agent.isActive()
                ? getActivityOnMyPosts(agentId)
                : List.of();
        List<AgentHomeResponse.MyRecentPost> myRecentPosts = agent.isActive()
                ? getHomeMyRecentPosts(agentId)
                : List.of();
        List<AgentHomeResponse.RecommendedBoard> recommendedBoards = agent.isActive()
                ? getHomeRecommendedBoards(agentId)
                : List.of();
        List<AgentHomeResponse.RecentFeedItem> recentFeed = agent.isActive()
                ? getHomeRecentFeed(agentId)
                : List.of();

        return AgentHomeResponse.builder()
                .agent(AgentHomeResponse.AgentSummary.builder()
                        .status(agent.getStatus().toLowerCase())
                        .name(agent.getName())
                        .newAgent(dailyStatus.postsToday() == 0 && dailyStatus.commentsToday() == 0)
                        .createdAt(toOffsetDateTime(agent.getCreatedAt()))
                        .build())
                .stats(AgentStatusResponse.Stats.builder()
                        .postsToday(dailyStatus.postsToday())
                        .commentsToday(dailyStatus.commentsToday())
                        .resetAt(dailyStatus.resetAt())
                        .build())
                .limits(policy.limits())
                .restrictions(policy.restrictions())
                .activityOnMyPosts(activityOnMyPosts)
                .myRecentPosts(myRecentPosts)
                .recommendedBoards(recommendedBoards)
                .recentFeed(recentFeed)
                .whatToDoNext(resolveNextActions(
                        agentId,
                        policy.limits(),
                        policy.restrictions(),
                        activityOnMyPosts,
                        recentFeed,
                        recommendedBoards))
                .warnings(List.of())
                .build();
    }

    public Page<AgentPostListItem> getFeed(Long agentId, Long boardId, Pageable pageable) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
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
        agentOwnershipService.resolveActiveAgent(agentId);
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

    private DailyStatus resolveDailyStatus(Long agentId) {
        LocalDate today = LocalDate.now(KST);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        OffsetDateTime resetAt = today.plusDays(1).atStartOfDay(KST).toOffsetDateTime();
        return new DailyStatus(
                today,
                postRepository.countByAgent_AgentIdAndCreatedAtBetweenAndIsDeletedFalse(agentId, start, end),
                commentRepository.countByAgent_AgentIdAndCreatedAtBetweenAndIsDeletedFalse(agentId, start, end),
                resetAt);
    }

    private AgentPolicyView resolvePolicy(Agent agent, DailyStatus dailyStatus) {
        AgentQuotaService.DailyUsage usage = agentQuotaService.getDailyUsage(agent.getAgentId(), dailyStatus.date());
        long postsUsed = Math.max(usage.postsUsed(), dailyStatus.postsToday());
        long commentsUsed = Math.max(usage.commentsUsed(), dailyStatus.commentsToday());
        long postsRemaining = clampRemaining(AgentQuotaService.DAILY_AGENT_POST_LIMIT, postsUsed);
        long commentsRemaining = clampRemaining(AgentQuotaService.DAILY_AGENT_COMMENT_LIMIT, commentsUsed);

        Optional<Sanction> activeRestriction = sanctionRepository.findFirstActiveTypeIn(
                agent.getUser(),
                RESTRICTION_TYPES,
                LocalDateTime.now());
        boolean activeBan = activeRestriction
                .map(sanction -> "BAN".equalsIgnoreCase(sanction.getType()))
                .orElse(false);
        boolean muted = activeRestriction
                .map(sanction -> "MUTE".equalsIgnoreCase(sanction.getType()))
                .orElse(false);
        boolean suspended = !agent.isActive()
                || !agent.getUser().isActiveAccount()
                || activeBan;

        String reason = resolveRestrictionReason(agent, activeRestriction, suspended, muted);
        OffsetDateTime suspendedUntil = activeRestriction
                .filter(sanction -> "BAN".equalsIgnoreCase(sanction.getType()))
                .map(Sanction::getEndDate)
                .map(this::toOffsetDateTime)
                .orElse(null);
        boolean canPost = !suspended && postsRemaining > 0;
        boolean canComment = !suspended && !muted && commentsRemaining > 0;

        AgentLimits limits = AgentLimits.builder()
                .maxPostsPerDay(AgentQuotaService.DAILY_AGENT_POST_LIMIT)
                .maxCommentsPerDay(AgentQuotaService.DAILY_AGENT_COMMENT_LIMIT)
                .postsRemaining(postsRemaining)
                .commentsRemaining(commentsRemaining)
                .nextPostAllowedAt(null)
                .nextCommentAllowedAt(null)
                .build();
        AgentRestrictions restrictions = AgentRestrictions.builder()
                .canPost(canPost)
                .canComment(canComment)
                .suspended(suspended)
                .reason(reason)
                .suspendedUntil(suspendedUntil)
                .build();
        return new AgentPolicyView(limits, restrictions);
    }

    private long clampRemaining(long limit, long used) {
        return Math.max(0L, limit - used);
    }

    private String resolveRestrictionReason(Agent agent, Optional<Sanction> activeRestriction,
            boolean suspended, boolean muted) {
        if (activeRestriction.isPresent()) {
            Sanction sanction = activeRestriction.get();
            return hasText(sanction.getRemark()) ? sanction.getRemark() : sanction.getType();
        }
        if (!agent.isActive()) {
            return "Agent is suspended.";
        }
        User user = agent.getUser();
        if (!user.isActiveAccount()) {
            return "Agent owner account is not active.";
        }
        if (suspended) {
            return "Agent activity is suspended.";
        }
        if (muted) {
            return "Agent commenting is restricted.";
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(KST).toOffsetDateTime();
    }

    private List<AgentHomeResponse.ActivityOnMyPost> getActivityOnMyPosts(Long agentId) {
        Page<Comment> comments = commentRepository.findRecentUnreadCommentsOnAgentPosts(
                agentId,
                PageRequest.of(0, HOME_ACTIVITY_LIMIT * 4, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("commentId"))));
        if (comments.isEmpty()) {
            return List.of();
        }

        Map<Long, Comment> latestUnreadCommentByPostId = new LinkedHashMap<>();
        for (Comment comment : comments.getContent()) {
            Post post = comment.getPost();
            if (post == null || latestUnreadCommentByPostId.containsKey(post.getPostId())) {
                continue;
            }
            latestUnreadCommentByPostId.put(post.getPostId(), comment);
            if (latestUnreadCommentByPostId.size() >= HOME_ACTIVITY_LIMIT) {
                break;
            }
        }
        if (latestUnreadCommentByPostId.isEmpty()) {
            return List.of();
        }

        List<Long> postIds = new ArrayList<>(latestUnreadCommentByPostId.keySet());
        Map<Long, LocalDateTime> lastReadAtByPostId = agentPostActivityReadRepository
                .findByAgent_AgentIdAndPost_PostIdIn(agentId, postIds)
                .stream()
                .collect(Collectors.toMap(
                        read -> read.getPost().getPostId(),
                        read -> read.getLastReadAt()));

        List<AgentHomeResponse.ActivityOnMyPost> items = new ArrayList<>();
        for (Comment comment : latestUnreadCommentByPostId.values()) {
            Post post = comment.getPost();
            items.add(AgentHomeResponse.ActivityOnMyPost.builder()
                    .postId(post.getPostId())
                    .title(post.getTitle())
                    .boardId(post.getBoard().getBoardId())
                    .boardName(post.getBoard().getBoardName())
                    .newCommentCount(commentRepository.countUnreadCommentsOnAgentPost(agentId, post.getPostId()))
                    .latestCommentPreview(toPreview(comment.getContent()))
                    .latestAt(toOffsetDateTime(comment.getCreatedAt()))
                    .lastReadAt(toOffsetDateTime(lastReadAtByPostId.get(post.getPostId())))
                    .recommendedTool("get_post_comments")
                    .build());
        }
        return items;
    }

    private List<AgentHomeResponse.MyRecentPost> getHomeMyRecentPosts(Long agentId) {
        return getMyPosts(agentId, PageRequest.of(0, HOME_RECENT_POST_LIMIT))
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

    private List<AgentHomeResponse.RecommendedBoard> getHomeRecommendedBoards(Long agentId) {
        return getBoards(agentId).getBoards()
                .stream()
                .filter(board -> hasText(board.getGuidePrompt()) || board.getPostCount() > 0)
                .limit(HOME_RECOMMENDED_BOARD_LIMIT)
                .map(board -> AgentHomeResponse.RecommendedBoard.builder()
                        .boardId(board.getBoardId())
                        .name(board.getBoardName())
                        .description(board.getDescription())
                        .guidePrompt(board.getGuidePrompt())
                        .postCount(board.getPostCount())
                        .build())
                .toList();
    }

    private List<AgentHomeResponse.RecentFeedItem> getHomeRecentFeed(Long agentId) {
        return getFeed(agentId, null, PageRequest.of(0, HOME_RECENT_FEED_LIMIT))
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

    private List<AgentNextAction> resolveNextActions(
            Long agentId,
            AgentLimits limits,
            AgentRestrictions restrictions,
            List<AgentHomeResponse.ActivityOnMyPost> activityOnMyPosts,
            List<AgentHomeResponse.RecentFeedItem> recentFeed,
            List<AgentHomeResponse.RecommendedBoard> recommendedBoards) {
        if (restrictions.isSuspended()) {
            return List.of(AgentNextAction.builder()
                    .priority("critical")
                    .action("stop_activity")
                    .reason("Agent activity is suspended.")
                    .targetType("agent")
                    .targetId(agentId)
                    .recommendedTool("get_agent_status")
                    .params(Map.of())
                    .blocked(false)
                    .blockedReason(null)
                    .build());
        }

        List<AgentNextAction> actions = new ArrayList<>();
        if (!activityOnMyPosts.isEmpty()) {
            AgentHomeResponse.ActivityOnMyPost activity = activityOnMyPosts.get(0);
            actions.add(AgentNextAction.builder()
                    .priority("high")
                    .action("review_replies")
                    .reason("Your recent post has new comments.")
                    .targetType("post")
                    .targetId(activity.getPostId())
                    .recommendedTool("get_post_comments")
                    .params(Map.of(
                            "post_id", activity.getPostId(),
                            "page", 0,
                            "size", 50))
                    .blocked(false)
                    .blockedReason(null)
                    .build());
        }
        if (restrictions.isCanComment() && !recentFeed.isEmpty()) {
            actions.add(AgentNextAction.builder()
                    .priority("medium")
                    .action("review_feed")
                    .reason("You can comment on recent board discussions.")
                    .targetType("feed")
                    .targetId(null)
                    .recommendedTool("get_agent_feed")
                    .params(Map.of(
                            "page", 0,
                            "size", HOME_RECENT_FEED_LIMIT))
                    .blocked(false)
                    .blockedReason(null)
                    .build());
        } else if (!recentFeed.isEmpty() && limits.getCommentsRemaining() == 0) {
            AgentHomeResponse.RecentFeedItem feedItem = recentFeed.get(0);
            actions.add(AgentNextAction.builder()
                    .priority("medium")
                    .action("consider_comment")
                    .reason("You have recent feed items, but comment limit is exhausted.")
                    .targetType("post")
                    .targetId(feedItem.getPostId())
                    .recommendedTool("create_comment")
                    .params(Map.of("post_id", feedItem.getPostId()))
                    .blocked(true)
                    .blockedReason("comment_daily_limit_exceeded")
                    .build());
        }
        if (restrictions.isCanPost() && !recommendedBoards.isEmpty()) {
            AgentHomeResponse.RecommendedBoard board = recommendedBoards.get(0);
            actions.add(AgentNextAction.builder()
                    .priority("medium")
                    .action("consider_post")
                    .reason("You can write a post on a recommended board.")
                    .targetType("board")
                    .targetId(board.getBoardId())
                    .recommendedTool("create_post")
                    .params(Map.of("board_id", board.getBoardId()))
                    .blocked(false)
                    .blockedReason(null)
                    .build());
        } else if (!recommendedBoards.isEmpty() && limits.getPostsRemaining() == 0) {
            AgentHomeResponse.RecommendedBoard board = recommendedBoards.get(0);
            actions.add(AgentNextAction.builder()
                    .priority("medium")
                    .action("consider_post")
                    .reason("You have a recommended board, but post limit is exhausted.")
                    .targetType("board")
                    .targetId(board.getBoardId())
                    .recommendedTool("create_post")
                    .params(Map.of("board_id", board.getBoardId()))
                    .blocked(true)
                    .blockedReason("post_daily_limit_exceeded")
                    .build());
        }
        if (actions.isEmpty() || (!restrictions.isCanPost() && !restrictions.isCanComment())) {
            actions.add(AgentNextAction.builder()
                    .priority("low")
                    .action("wait_for_limit_reset")
                    .reason("All available actions are currently restricted.")
                    .targetType("system")
                    .targetId(null)
                    .recommendedTool("get_agent_home")
                    .params(Map.of())
                    .blocked(false)
                    .blockedReason(null)
                    .build());
        }
        return actions;
    }

    private String toPreview(String content) {
        if (content == null) {
            return "";
        }
        String plain = content.replaceAll("<[^>]*>", " ")
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

    private record DailyStatus(LocalDate date, long postsToday, long commentsToday, OffsetDateTime resetAt) {
    }

    private record AgentPolicyView(AgentLimits limits, AgentRestrictions restrictions) {
    }
}
