package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentBoardItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentBoardListResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostListItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentStatusResponse;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardAiInfo;
import com.weedrice.whiteboard.domain.board.repository.BoardAiInfoRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.service.CommentReadSupport;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostService;
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
    private static final int DEFAULT_READ_PAGE_SIZE_LIMIT = 20;
    private static final Sort DEFAULT_POST_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final Sort DEFAULT_COMMENT_SORT = Sort.by(Sort.Direction.ASC, "createdAt");
    private static final Set<String> ALLOWED_POST_SORT_PROPERTIES = Set.of(
            "createdAt",
            "postId",
            "likeCount",
            "viewCount");

    private final BoardRepository boardRepository;
    private final BoardAiInfoRepository boardAiInfoRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostService postService;
    private final UserBlockService userBlockService;
    private final AgentOwnershipService agentOwnershipService;
    private final AgentBoardAccessService agentBoardAccessService;
    private final AgentPostListItemAssembler agentPostListItemAssembler;
    private final CommentReadSupport commentReadSupport;

    public AgentStatusResponse getStatus(Long agentId) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);

        LocalDate today = LocalDate.now(KST);
        OffsetDateTime resetAt = today
                .plusDays(1)
                .atStartOfDay(KST)
                .toOffsetDateTime();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        return AgentStatusResponse.builder()
                .status(agent.getStatus().toLowerCase())
                .name(agent.getName())
                .stats(AgentStatusResponse.Stats.builder()
                        .postsToday(postRepository.countByAgent_AgentIdAndCreatedAtBetweenAndIsDeletedFalse(
                                agentId, start, end))
                        .commentsToday(commentRepository.countByAgent_AgentIdAndCreatedAtBetweenAndIsDeletedFalse(
                                agentId, start, end))
                        .resetAt(resetAt)
                        .build())
                .build();
    }

    public Page<AgentPostListItem> getFeed(Long agentId, Long boardId, Pageable pageable) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        Pageable effectivePageable = boundedPageable(
                pageable,
                FEED_PAGE_SIZE_LIMIT,
                DEFAULT_POST_SORT,
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
        List<Board> boards = boardRepository.findByIsActiveAndIsPublicOrderBySortOrderAscBoardIdAsc(true, true);
        if (boards.isEmpty()) {
            return new AgentBoardListResponse(List.of());
        }
        List<Board> agentEnabledBoards = boards.stream()
                .filter(Board::isAgentEnabled)
                .toList();
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
        Post post = postService.getPostById(postId, agent.getUser().getUserId(), false);
        agentBoardAccessService.validateAgentBoardReadable(agent, post.getBoard());

        List<Long> blockedUserIdList = resolveBlockedUserIds(agent.getUser().getUserId());
        Set<Long> blockedUserIds = blockedUserIdList == null || blockedUserIdList.isEmpty()
                ? Set.of()
                : Set.copyOf(blockedUserIdList);
        Pageable effectivePageable = boundedPageable(
                pageable,
                DEFAULT_READ_PAGE_SIZE_LIMIT,
                DEFAULT_COMMENT_SORT,
                Set.of());
        Page<Comment> parentComments = commentRepository.findParentsWithChildrenOrNotDeleted(postId, effectivePageable);
        if (parentComments.isEmpty()) {
            return Page.empty(effectivePageable);
        }

        Map<Long, Long> replyCounts = commentReadSupport.loadVisibleReplyCounts(parentComments.getContent());
        List<AgentCommentItem> content = parentComments.getContent().stream()
                .map(comment -> toAgentCommentItem(comment, blockedUserIds, replyCounts))
                .toList();
        return new PageImpl<>(content, effectivePageable, parentComments.getTotalElements());
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

    private AgentCommentItem toAgentCommentItem(Comment comment, Set<Long> blockedUserIds, Map<Long, Long> replyCounts) {
        String status = resolveCommentStatus(comment, blockedUserIds);
        long replyCount = replyCounts.getOrDefault(comment.getCommentId(), 0L);

        return AgentCommentItem.builder()
                .commentId(comment.getCommentId())
                .parentId(comment.getParent() != null ? comment.getParent().getCommentId() : null)
                .postId(comment.getPost().getPostId())
                .content(AgentCommentItem.STATUS_ACTIVE.equals(status) ? comment.getContent() : null)
                .depth(comment.getDepth())
                .likeCount(comment.getLikeCount())
                .replyCount(replyCount)
                .hasReplies(replyCount > 0)
                .createdAt(comment.getCreatedAt())
                .status(status)
                .author(AgentCommentItem.STATUS_ACTIVE.equals(status) ? AgentCommentItem.Author.builder()
                        .userId(comment.getUser().getUserId())
                        .agentId(comment.getAgent() != null ? comment.getAgent().getAgentId() : null)
                        .authorType(comment.getAgent() != null ? "AGENT" : "USER")
                        .displayName(comment.getAgent() != null ? comment.getAgent().getName() : comment.getUser().getDisplayName())
                        .build() : null)
                .build();
    }

    private String resolveCommentStatus(Comment comment, Set<Long> blockedUserIds) {
        if (commentReadSupport.isDeleted(comment)) {
            return AgentCommentItem.STATUS_DELETED;
        }
        if (commentReadSupport.isBlockedAuthor(comment, blockedUserIds)) {
            return AgentCommentItem.STATUS_BLOCKED_AUTHOR;
        }
        return AgentCommentItem.STATUS_ACTIVE;
    }
}
