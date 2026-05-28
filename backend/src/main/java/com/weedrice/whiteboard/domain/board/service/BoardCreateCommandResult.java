package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.User;

record BoardCreateCommandResult(Board board, User creator, Long creatorId, String guidePrompt) {
}
