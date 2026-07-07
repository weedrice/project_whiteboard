package com.weedrice.whiteboard.domain.comment.service;

import java.util.Collection;

public record CommentUpdateCommand(
        Long userId,
        Long commentId,
        String content,
        Collection<Long> mentionedUserIds) {
}

