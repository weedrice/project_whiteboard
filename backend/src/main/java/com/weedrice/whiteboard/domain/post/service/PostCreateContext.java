package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;

public record PostCreateContext(
        Agent agent,
        Board board,
        BoardCategory category,
        boolean boardWritablePrevalidated) {

    public static PostCreateContext agent(Agent agent, Board board, BoardCategory category) {
        return new PostCreateContext(agent, board, category, true);
    }
}
