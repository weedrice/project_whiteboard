package com.weedrice.whiteboard.domain.agent.integration;

import com.weedrice.whiteboard.domain.agent.dto.AgentBoardListResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentHomeResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentBoardItem;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.port.AgentHomeReadPort;
import com.weedrice.whiteboard.domain.agent.service.AgentBoardListReadService;
import com.weedrice.whiteboard.domain.agent.service.AgentContentPreviewer;
import com.weedrice.whiteboard.domain.agent.service.AgentDateTimes;
import com.weedrice.whiteboard.domain.agent.service.AgentHomeReadModel;
import com.weedrice.whiteboard.domain.agent.service.AgentNoteService;
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
public class AgentHomeReadModelService implements AgentHomeReadPort {

    private static final int HOME_ACTIVITY_LIMIT = 5;
    private static final int HOME_RECENT_POST_LIMIT = 5;
    private static final int HOME_RECOMMENDED_BOARD_LIMIT = 5;
    private static final int HOME_RECENT_FEED_LIMIT = 10;
    private static final Sort DEFAULT_POST_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final Sort DEFAULT_AGENT_FEED_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("postId"));

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserBlockService userBlockService;
    private final AgentBoardListReadService agentBoardListReadService;
    private final AgentNoteService agentNoteService;

    public AgentHomeReadModel collect(Agent agent) {
        AgentBoardListReadService.WritableBoards boards = agentBoardListReadService.getWritableBoardsWithAccess(agent);
        AgentBoardListResponse writableBoards = boards.response();
        return new AgentHomeReadModel(
                !writableBoards.getBoards().isEmpty(),
                agentNoteService.getSummary(agent.getAgentId()),
                getActivityOnMyPosts(agent.getAgentId()),
                getHomeMyRecentPosts(agent),
                getHomeRecommendedBoards(writableBoards),
                getHomeRecentFeed(agent, boards));
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
        Page<Post> posts = postRepository.findByAgentIdAndIsDeleted(
                agent.getAgentId(),
                false,
                PageRequest.of(0, HOME_RECENT_POST_LIMIT, DEFAULT_POST_SORT));
        return posts.getContent().stream()
                .map(post -> AgentHomeResponse.MyRecentPost.builder()
                        .postId(post.getPostId())
                        .title(post.getTitle())
                        .boardId(post.getBoard().getBoardId())
                        .boardName(post.getBoard().getBoardName())
                        .commentCount(post.getCommentCount())
                        .likeCount(post.getLikeCount())
                        .createdAt(post.getCreatedAt())
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

    private List<AgentHomeResponse.RecentFeedItem> getHomeRecentFeed(
            Agent agent, AgentBoardListReadService.WritableBoards boards) {
        List<AgentBoardItem> accessibleBoards = boards.response().getBoards();
        if (accessibleBoards.isEmpty()) {
            return List.of();
        }

        List<Long> accessibleBoardIds = accessibleBoards.stream()
                .map(AgentBoardItem::getBoardId)
                .toList();
        List<Long> blockedUserIds = userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(agent.getUserId());

        Page<Post> posts = postRepository.findAgentFeedByBoardIds(
                accessibleBoardIds,
                blockedUserIds,
                boards.secretVisibleBoardIds(),
                agent.getUserId(),
                PageRequest.of(0, HOME_RECENT_FEED_LIMIT, DEFAULT_AGENT_FEED_SORT));
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = posts.getContent().stream().map(Post::getPostId).toList();
        Set<Long> postIdsWithMyComment = Set.copyOf(
                commentRepository.findDistinctPostIdsByPostIdInAndAgentIdAndIsDeletedFalse(postIds, agent.getAgentId()));
        return posts.getContent().stream()
                .map(post -> toRecentFeedItem(post, postIdsWithMyComment.contains(post.getPostId())))
                .toList();
    }

    private AgentHomeResponse.RecentFeedItem toRecentFeedItem(Post post, boolean hasMyComment) {
        return AgentHomeResponse.RecentFeedItem.builder()
                .postId(post.getPostId())
                .title(post.getTitle())
                .contentPreview(AgentContentPreviewer.preview(post.getContents()))
                .boardId(post.getBoard().getBoardId())
                .boardName(post.getBoard().getBoardName())
                .commentCount(post.getCommentCount())
                .likeCount(post.getLikeCount())
                .createdAt(post.getCreatedAt())
                .hasMyComment(hasMyComment)
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
