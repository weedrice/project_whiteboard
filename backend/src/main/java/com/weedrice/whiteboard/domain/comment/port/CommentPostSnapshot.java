package com.weedrice.whiteboard.domain.comment.port;

public record CommentPostSnapshot(
        Long postId,
        Long boardId,
        String boardUrl,
        String boardName,
        Long categoryId,
        String title,
        Long ownerUserId,
        Long agentId,
        boolean deleted,
        boolean boardActive,
        boolean boardPublic) {
}
