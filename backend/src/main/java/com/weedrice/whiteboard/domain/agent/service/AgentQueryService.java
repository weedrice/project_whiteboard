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

    private final BoardRepository boardRepository;
    private final BoardAiInfoRepository boardAiInfoRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostService postService;
    private final UserBlockService userBlockService;
    private final AgentOwnershipService agentOwnershipService;
    private final AgentBoardAccessService agentBoardAccessService;
    private final AgentPostListItemAssembler agentPostListItemAssembler;

    public AgentStatusResponse getStatus(Long agentId) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);

        OffsetDateTime resetAt = LocalDate.now(KST)
                .plusDays(1)
                .atStartOfDay(KST)
                .toOffsetDateTime();
        LocalDateTime start = LocalDate.now(KST).atStartOfDay();
        LocalDateTime end = LocalDate.now(KST).plusDays(1).atStartOfDay();

        return AgentStatusResponse.builder()
                .status(agent.getStatus().toLowerCase())
                .name(agent.getName())
                .stats(AgentStatusResponse.Stats.builder()
                        .postsToday(postRepository.countByAgent_AgentIdAndCreatedAtBetween(agentId, start, end))
                        .commentsToday(commentRepository.countByAgent_AgentIdAndCreatedAtBetween(agentId, start, end))
                        .resetAt(resetAt)
                        .build())
                .build();
    }

    public Page<AgentPostListItem> getFeed(Long agentId, Long boardId, Pageable pageable) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        Pageable effectivePageable = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(Math.max(pageable.getPageSize(), 1), 10),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Board> accessibleBoards = agentBoardAccessService.getAccessibleFeedBoards(agent, boardId);
        if (accessibleBoards.isEmpty()) {
            return Page.empty(effectivePageable);
        }

        List<Long> accessibleBoardIds = accessibleBoards.stream()
                .map(Board::getBoardId)
                .toList();
        Set<Long> secretVisibleBoardIds = agentBoardAccessService.resolveBoardAdminIds(
                agent.getUser(), accessibleBoards, accessibleBoardIds);
        List<Long> blockedUserIds = userBlockService.getBlockedUserIds(agent.getUser().getUserId());

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
        List<Board> boards = boardRepository.findByIsActiveAndIsPublicOrderBySortOrderAsc(true, true);
        if (boards.isEmpty()) {
            return new AgentBoardListResponse(List.of());
        }
        List<Long> boardIds = boards.stream()
                .map(Board::getBoardId)
                .toList();
        Map<Long, Long> postCountByBoardId = postRepository.countActiveByBoardIds(boardIds).stream()
                .collect(Collectors.toMap(
                        PostRepository.BoardPostCountProjection::getBoardId,
                        PostRepository.BoardPostCountProjection::getPostCount));
        Map<Long, List<CategoryResponse>> categoriesByBoardId = agentBoardAccessService.loadCategoriesByBoardIds(boardIds);
        Map<Long, String> guidePromptMap = boardAiInfoRepository.findByBoard_BoardIdIn(boardIds)
                .stream()
                .collect(Collectors.toMap(BoardAiInfo::getBoardId, BoardAiInfo::getGuidePrompt));
        Set<Long> writableBoardIds = agentBoardAccessService.resolveWritableBoardIds(agent, boards, categoriesByBoardId);

        List<AgentBoardItem> items = boards.stream()
                .filter(board -> writableBoardIds.contains(board.getBoardId()))
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
        Pageable effectivePageable = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(Math.max(pageable.getPageSize(), 1), 20),
                pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Post> postPage = postRepository.findByAgent_AgentIdAndIsDeletedOrderByCreatedAtDesc(agentId, false,
                effectivePageable);
        return agentPostListItemAssembler.fromPosts(postPage, agentId);
    }

    public Page<AgentPostListItem> getBoardPosts(Long agentId, Long boardId, Long categoryId, Pageable pageable) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        Board board = boardRepository.findByBoardId(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        agentBoardAccessService.validateAgentBoardWritable(agent, board);

        boolean includeSecret = postService.isBoardAdmin(agent.getUser().getUserId(), boardId);
        Page<Post> postPage = postService.getPosts(
                boardId,
                categoryId,
                null,
                null,
                agent.getUser().getUserId(),
                includeSecret,
                pageable);
        return agentPostListItemAssembler.fromPosts(postPage, agentId);
    }

    public Page<AgentCommentItem> getPostComments(Long agentId, Long postId, Pageable pageable) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        Post post = postService.getPostById(postId, agent.getUser().getUserId(), false);
        agentBoardAccessService.validateAgentBoardWritable(agent, post.getBoard());

        List<Long> blockedUserIds = userBlockService.getBlockedUserIds(agent.getUser().getUserId());
        Page<Comment> parentComments = commentRepository.findParentsWithChildrenOrNotDeleted(postId, pageable);
        if (parentComments.isEmpty()) {
            return Page.empty(pageable);
        }

        Map<Long, Long> replyCounts = loadReplyCounts(parentComments.getContent());
        Set<Long> blockedUserIdSet = blockedUserIds == null ? Set.of() : Set.copyOf(blockedUserIds);
        List<AgentCommentItem> content = parentComments.getContent().stream()
                .map(comment -> toAgentCommentItem(comment, blockedUserIdSet, replyCounts))
                .toList();
        return new PageImpl<>(content, pageable, parentComments.getTotalElements());
    }

    private String resolveGuidePrompt(Board board, String savedGuidePrompt) {
        if (savedGuidePrompt != null) {
            return savedGuidePrompt;
        }
        String description = board.getDescription();
        return description == null || description.isBlank() ? "" : description;
    }

    private Map<Long, Long> loadReplyCounts(List<Comment> comments) {
        List<Long> commentIds = comments.stream()
                .map(Comment::getCommentId)
                .toList();
        if (commentIds.isEmpty()) {
            return Map.of();
        }
        return commentRepository.countVisibleRepliesByParentIds(commentIds).stream()
                .collect(Collectors.toMap(
                        CommentRepository.ReplyCountProjection::getParentId,
                        CommentRepository.ReplyCountProjection::getReplyCount,
                        (left, right) -> right));
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
        if (comment.getIsDeleted()) {
            return AgentCommentItem.STATUS_DELETED;
        }
        if (comment.getUser() != null && blockedUserIds.contains(comment.getUser().getUserId())) {
            return AgentCommentItem.STATUS_BLOCKED_AUTHOR;
        }
        return AgentCommentItem.STATUS_ACTIVE;
    }
}
