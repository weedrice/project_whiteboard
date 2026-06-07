package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentBoardListResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentHomeResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostListItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentProfileResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentStatusResponse;
import com.weedrice.whiteboard.domain.agent.service.AgentPolicyService.AgentDailyStatus;
import com.weedrice.whiteboard.domain.agent.service.AgentPolicyService.AgentPolicySnapshot;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
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
import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
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

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentQueryService {

    private static final ZoneId KST = DateTimeUtils.KST_ZONE_ID;
    private static final int FEED_PAGE_SIZE_LIMIT = 10;
    private static final int DEFAULT_READ_PAGE_SIZE_LIMIT = 20;
    private static final Sort DEFAULT_POST_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final Sort DEFAULT_AGENT_FEED_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("postId"));
    private static final Sort DEFAULT_COMMENT_SORT = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("commentId"));
    private static final Set<String> ALLOWED_POST_SORT_PROPERTIES = Set.of(
            "createdAt",
            "postId",
            "likeCount",
            "viewCount");

    private final BoardRepository boardRepository;
    private final AgentRepository agentRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostService postService;
    private final PostAccessPolicy postAccessPolicy;
    private final UserBlockService userBlockService;
    private final AgentOwnershipService agentOwnershipService;
    private final AgentBoardAccessService agentBoardAccessService;
    private final AgentBoardListReadService agentBoardListReadService;
    private final AgentPostListItemAssembler agentPostListItemAssembler;
    private final CommentReadSupport commentReadSupport;
    private final CommentReadModelAssembler commentReadModelAssembler;
    private final AgentPolicyService agentPolicyService;
    private final AgentHomeReadModelService agentHomeReadModelService;
    private final AgentHomeResponseAssembler agentHomeResponseAssembler;

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
        boolean activeAgent = agent.isActive();
        if (activeAgent) {
            agentOwnershipService.validateAuthenticatedAgent(agent);
        }

        AgentHomeReadModel homeReadModel = activeAgent
                ? agentHomeReadModelService.collect(agent)
                : AgentHomeReadModel.empty();
        return agentHomeResponseAssembler.assemble(agent, policy, homeReadModel);
    }

    public Page<AgentPostListItem> getFeed(Long agentId, Long boardId, Pageable pageable) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        return getFeed(agent, boardId, pageable);
    }

    private Page<AgentPostListItem> getFeed(Agent agent, Long boardId, Pageable pageable) {
        Long agentId = agent.getAgentId();
        Pageable effectivePageable = PageRequestUtils.bounded(
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
        return agentBoardListReadService.getWritableBoards(agent);
    }

    public Page<AgentPostListItem> getMyPosts(Long agentId, Pageable pageable) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        return getMyPosts(agent, pageable);
    }

    private Page<AgentPostListItem> getMyPosts(Agent agent, Pageable pageable) {
        Long agentId = agent.getAgentId();
        Pageable effectivePageable = PageRequestUtils.bounded(
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
                PageRequestUtils.bounded(
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

        Pageable effectivePageable = PageRequestUtils.bounded(
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

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(KST).toOffsetDateTime();
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
        return AgentContentPreviewer.preview(content);
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
