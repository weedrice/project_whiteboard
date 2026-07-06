package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.User;

record BoardUpdateCommandResult(Board board, User currentUser, String previousIconUrl, String previousBoardName,
        Boolean previousIsActive, Boolean previousIsPublic, String guidePrompt) {
}
