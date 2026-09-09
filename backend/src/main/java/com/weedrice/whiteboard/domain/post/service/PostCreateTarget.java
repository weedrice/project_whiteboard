package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.actor.ActorUserPrincipal;
import com.weedrice.whiteboard.domain.board.entity.Board;

record PostCreateTarget(
        ActorUserPrincipal user,
        Long agentId,
        Board board,
        boolean boardWritablePrevalidated) {
}
