package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;

public record PostCreateContext(
        Long ownerUserId,
        Long agentId,
        Board board,
        BoardCategory category,
        boolean boardWritablePrevalidated) {

    public static PostCreateContext agent(
            Long ownerUserId, Long agentId, Board board, BoardCategory category) {
        return new PostCreateContext(ownerUserId, agentId, board, category, true);
    }
}
