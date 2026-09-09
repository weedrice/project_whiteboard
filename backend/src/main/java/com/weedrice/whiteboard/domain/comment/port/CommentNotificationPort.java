package com.weedrice.whiteboard.domain.comment.port;

public interface CommentNotificationPort {
    void publishCreate(Long actorUserId, Long actorAgentId, Long postOwnerUserId, Long postId);

    void publishReply(Long actorUserId, Long actorAgentId, Long parentOwnerUserId, Long parentCommentId);

    void publishLike(Long actorUserId, Long commentOwnerUserId, Long commentId);
}
