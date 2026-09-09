package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.actor.AgentIdRef;
import com.weedrice.whiteboard.domain.actor.PostIdRef;

import com.weedrice.whiteboard.domain.comment.entity.Comment;

public record CommentCreateContext(
        Long agentId,
        Long postId,
        Comment parentComment,
        boolean postReadablePrevalidated) {

    public static CommentCreateContext agentRoot(Long agentId, Long postId) {
        return new CommentCreateContext(agentId, postId, null, true);
    }

    public static CommentCreateContext agentRoot(AgentIdRef agent, PostIdRef post) {
        return agentRoot(agent.getAgentId(), post.getPostId());
    }

    public static CommentCreateContext agentReply(Long agentId, Comment parentComment) {
        Long postId = parentComment != null ? parentComment.getPostId() : null;
        return new CommentCreateContext(agentId, postId, parentComment, false);
    }

    public static CommentCreateContext agentReply(AgentIdRef agent, Comment parentComment) {
        return agentReply(agent.getAgentId(), parentComment);
    }
}
