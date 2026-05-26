package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentBoardItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentBoardListResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentHomeResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostListItem;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardAiInfo;
import com.weedrice.whiteboard.domain.board.repository.BoardAiInfoRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentHomeReadModelService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int HOME_ACTIVITY_LIMIT = 5;
    private static final int HOME_RECENT_POST_LIMIT = 5;
    private static final int HOME_RECOMMENDED_BOARD_LIMIT = 5;
    private static final int HOME_RECENT_FEED_LIMIT = 10;
    private static final Sort DEFAULT_POST_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final Sort DEFAULT_AGENT_FEED_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("postId"));

    private final BoardRepository boardRepository;
    private final BoardAiInfoRepository boardAiInfoRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserBlockService userBlockService;
    private final AgentBoardAccessService agentBoardAccessService;
    private final AgentPostListItemAssembler agentPostListItemAssembler;
    private final AgentNoteService agentNoteService;

    public AgentHomeReadModel collect(Agent agent) {
        AgentBoardListResponse writableBoards = getBoards(agent);
        return new AgentHomeReadModel(
                !writableBoards.getBoards().isEmpty(),
                agentNoteService.getSummary(agent.getAgentId()),
                getActivityOnMyPosts(agent.getAgentId()),
                getHomeMyRecentPosts(agent),
                getHomeRecommendedBoards(writableBoards),
                getHomeRecentFeed(agent));
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
        Page<Post> posts = postRepository.findByAgent_AgentIdAndIsDeleted(
                agent.getAgentId(),
                false,
                PageRequest.of(0, HOME_RECENT_POST_LIMIT, DEFAULT_POST_SORT));
        return agentPostListItemAssembler.fromPosts(posts, agent.getAgentId())
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
        Long agentId = agent.getAgentId();
        List<Board> accessibleBoards = agentBoardAccessService.getAccessibleFeedBoards(agent, null);
        if (accessibleBoards.isEmpty()) {
            return List.of();
        }

        List<Long> accessibleBoardIds = accessibleBoards.stream()
                .map(Board::getBoardId)
                .toList();
        Set<Long> secretVisibleBoardIds = agentBoardAccessService.resolveBoardAdminIds(
                agent.getUser(), accessibleBoards, accessibleBoardIds);
        List<Long> blockedUserIds = userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(agent.getUser().getUserId());

        Page<Post> posts = postRepository.findAgentFeedByBoardIds(
                accessibleBoardIds,
                blockedUserIds,
                secretVisibleBoardIds,
                agent.getUser().getUserId(),
                PageRequest.of(0, HOME_RECENT_FEED_LIMIT, DEFAULT_AGENT_FEED_SORT));
        return agentPostListItemAssembler.fromPosts(posts, agentId)
                .getContent()
                .stream()
                .map(this::toRecentFeedItem)
                .toList();
    }

    private AgentHomeResponse.RecentFeedItem toRecentFeedItem(AgentPostListItem item) {
        return AgentHomeResponse.RecentFeedItem.builder()
                .postId(item.getPostId())
                .title(item.getTitle())
                .contentPreview(toPreview(item.getContent()))
                .boardId(item.getBoardId())
                .boardName(item.getBoardName())
                .commentCount(item.getCommentCount())
                .likeCount(item.getLikeCount())
                .createdAt(item.getCreatedAt())
                .hasMyComment(item.isHasMyComment())
                .build();
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(KST).toOffsetDateTime();
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

    private String resolveGuidePrompt(Board board, String savedGuidePrompt) {
        if (savedGuidePrompt != null) {
            return savedGuidePrompt;
        }
        String description = board.getDescription();
        return description == null || description.isBlank() ? "" : description;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
