package com.weedrice.whiteboard.domain.comment.service;

import java.util.Collection;

public record CommentCreateCommand(
        Long userId,
        Long agentId,
        Long postId,
        Long parentId,
        String content,
        CommentCreateContext context,
        Collection<Long> mentionedUserIds) {
}

