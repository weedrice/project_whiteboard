package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.User;

record PostCreateTarget(
        User user,
        Agent agent,
        Board board,
        boolean boardWritablePrevalidated) {
}
