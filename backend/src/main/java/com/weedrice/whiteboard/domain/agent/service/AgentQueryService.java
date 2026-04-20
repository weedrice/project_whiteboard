package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentBoardItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentBoardListResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentStatusResponse;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardAiInfo;
import com.weedrice.whiteboard.domain.board.repository.BoardAiInfoRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
    private final CommentService commentService;
    private final UserBlockService userBlockService;
    private final AgentOwnershipService agentOwnershipService;
    private final AgentBoardAccessService agentBoardAccessService;
    private final AgentPostSummaryEnricher agentPostSummaryEnricher;

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

    public Page<PostSummary> getFeed(Long agentId, Long boardId, Pageable pageable) {
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
        return agentPostSummaryEnricher.fromPosts(posts, agentId);
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

    public Page<PostSummary> getMyPosts(Long agentId, Pageable pageable) {
        agentOwnershipService.resolveActiveAgent(agentId);
        Pageable effectivePageable = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(Math.max(pageable.getPageSize(), 1), 20),
                pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Post> postPage = postRepository.findByAgent_AgentIdAndIsDeletedOrderByCreatedAtDesc(agentId, false,
                effectivePageable);
        return agentPostSummaryEnricher.fromPosts(postPage, agentId);
    }

    public Page<PostSummary> getBoardPosts(Long agentId, Long boardId, Long categoryId, Pageable pageable) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        Board board = boardRepository.findByBoardId(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        agentBoardAccessService.validateAgentBoardWritable(agent, board);
        Page<PostSummary> postPage = postService.getPosts(board.getBoardUrl(), categoryId, null, null,
                agent.getUser().getUserId(), pageable);
        return agentPostSummaryEnricher.enrich(postPage, agentId);
    }

    public Page<CommentResponse> getPostComments(Long agentId, Long postId, Pageable pageable) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        Post post = postService.getPostById(postId, agent.getUser().getUserId(), false);
        agentBoardAccessService.validateAgentBoardWritable(agent, post.getBoard());
        return commentService.getComments(postId, agent.getUser().getUserId(), pageable);
    }

    private String resolveGuidePrompt(Board board, String savedGuidePrompt) {
        if (savedGuidePrompt != null) {
            return savedGuidePrompt;
        }
        String description = board.getDescription();
        return description == null || description.isBlank() ? "" : description;
    }
}
