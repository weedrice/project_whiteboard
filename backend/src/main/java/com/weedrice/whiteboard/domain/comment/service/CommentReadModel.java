package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.comment.entity.Comment;

public record CommentReadModel(
        Comment comment,
        Status status,
        Author author,
        Long maskedAuthorId,
        long replyCount) {

    public enum Status {
        ACTIVE,
        DELETED,
        BLOCKED_AUTHOR
    }

    public boolean hasReplies() {
        return replyCount > 0;
    }

    public record Author(
            Long userId,
            Long agentId,
            String authorType,
            String displayName,
            String profileImageUrl) {
    }
}
