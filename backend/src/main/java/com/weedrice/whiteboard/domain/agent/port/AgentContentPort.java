package com.weedrice.whiteboard.domain.agent.port;

import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostListItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentProfileResponse;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/** Consumer-owned boundary for all post/comment work initiated by the agent domain. */
public interface AgentContentPort {

    Long createPost(Agent agent, AgentPostCreateRequest request);

    DeletePostResult deletePost(Agent agent, Long postId);

    Long createComment(Agent agent, Long postId, AgentCommentCreateRequest request);

    Long createReply(Agent agent, Long commentId, AgentCommentCreateRequest request);

    LikeResult likePost(Agent agent, Long postId);

    LikeResult likeComment(Agent agent, Long commentId);

    AgentProfileContent getProfileContent(Agent viewer, Agent target, int fetchSize, int resultLimit);

    Page<AgentPostListItem> getFeed(Agent agent, Long boardId, Pageable pageable);

    Page<AgentPostListItem> getMyPosts(Agent agent, Pageable pageable);

    Page<AgentPostListItem> getBoardPosts(Agent agent, Long boardId, Long categoryId, Pageable pageable);

    Page<AgentCommentItem> getPostComments(Agent agent, Long postId, Pageable pageable);

    Page<AgentCommentItem> getCommentReplies(Agent agent, Long commentId, Pageable pageable);

    boolean isOwnerEmailVerified(Agent agent);

    void validateProfileVisible(Agent viewer, Agent target);

    record DeletePostResult(boolean alreadyDeleted, LocalDateTime deletedAt) {
    }

    record LikeResult(int likeCount, boolean alreadyLiked) {
    }

    record AgentProfileContent(
            long postsCount,
            long commentsCount,
            long likesReceivedCount,
            List<AgentProfileResponse.RecentPost> recentPosts,
            List<AgentProfileResponse.RecentComment> recentComments,
            List<AgentProfileResponse.PrimaryBoard> primaryBoards) {
    }
}
