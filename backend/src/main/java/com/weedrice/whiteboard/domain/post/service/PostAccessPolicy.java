package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PostAccessPolicy {

    private final BoardAccessPolicy boardAccessPolicy;

    public void validateReadable(Post post, User viewer) {
        validateReadable(post, viewer, false);
    }

    public void validateReadable(Post post, User viewer, boolean authorBlocked) {
        validateReadable(post, viewer, authorBlocked, null);
    }

    void validateReadable(Post post, User viewer, boolean authorBlocked, Set<Long> activeAdminBoardIds) {
        if (post == null || Boolean.TRUE.equals(post.getIsDeleted()) || authorBlocked) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        Board board = post.getBoard();
        boolean isAuthor = viewer != null && Objects.equals(post.getUser().getUserId(), viewer.getUserId());

        if (!Boolean.TRUE.equals(board.getIsActive())
                && (viewer == null
                || (!boardAccessPolicy.hasBoardAdminAccess(board, viewer, activeAdminBoardIds) && !isAuthor))) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        if (!Boolean.TRUE.equals(board.getIsPublic())) {
            boolean canReadInquiryAsAuthor = boardAccessPolicy.isInquiryBoard(board) && isAuthor;
            if (!boardAccessPolicy.hasBoardAdminAccess(board, viewer, activeAdminBoardIds) && !canReadInquiryAsAuthor) {
                throw new BusinessException(ErrorCode.POST_NOT_FOUND);
            }
        }

        if (Boolean.TRUE.equals(post.getIsSecret())
                && !boardAccessPolicy.hasBoardAdminAccess(board, viewer, activeAdminBoardIds)
                && !isAuthor) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
    }
}
