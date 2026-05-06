package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.post.entity.Post;

public record CommentCreateContext(
        Agent agent,
        Post post,
        Comment parentComment,
        boolean postReadablePrevalidated) {

    public static CommentCreateContext agentRoot(Agent agent, Post post) {
        return new CommentCreateContext(agent, post, null, true);
    }

    public static CommentCreateContext agentReply(Agent agent, Comment parentComment) {
        Post post = parentComment != null ? parentComment.getPost() : null;
        return new CommentCreateContext(agent, post, parentComment, false);
    }
}
