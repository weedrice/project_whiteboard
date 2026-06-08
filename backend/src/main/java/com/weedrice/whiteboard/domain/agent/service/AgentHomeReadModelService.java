package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentBoardListResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentHomeResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostListItem;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentHomeReadModelService {

    private static final int HOME_ACTIVITY_LIMIT = 5;
    private static final int HOME_RECENT_POST_LIMIT = 5;
    private static final int HOME_RECOMMENDED_BOARD_LIMIT = 5;
    private static final int HOME_RECENT_FEED_LIMIT = 10;
    private static final Sort DEFAULT_POST_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final Sort DEFAULT_AGENT_FEED_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("postId"));

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserBlockService userBlockService;
    private final AgentBoardAccessService agentBoardAccessService;
    private final AgentBoardListReadService agentBoardListReadService;
    private final AgentPostListItemAssembler agentPostListItemAssembler;
    private final AgentNoteService agentNoteService;

    public AgentHomeReadModel collect(Agent agent) {
        AgentBoardListResponse writableBoards = agentBoardListReadService.getWritableBoards(agent);
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
                    .latestCommentPreview(AgentContentPreviewer.preview(activity.getLatestCommentContent()))
                    .latestAt(AgentDateTimes.toOffsetDateTime(activity.getLatestCommentCreatedAt()))
                    .lastReadAt(AgentDateTimes.toOffsetDateTime(activity.getLastReadAt()))
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
                .contentPreview(AgentContentPreviewer.preview(item.getContent()))
                .boardId(item.getBoardId())
                .boardName(item.getBoardName())
                .commentCount(item.getCommentCount())
                .likeCount(item.getLikeCount())
                .createdAt(item.getCreatedAt())
                .hasMyComment(item.isHasMyComment())
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
