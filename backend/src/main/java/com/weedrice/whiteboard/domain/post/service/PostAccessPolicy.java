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
        if (!isReadable(post, viewer, authorBlocked, activeAdminBoardIds)) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
    }

    public boolean isReadable(Post post, User viewer) {
        return isReadable(post, viewer, false);
    }

    public boolean isReadable(Post post, User viewer, boolean authorBlocked) {
        return isReadable(post, viewer, authorBlocked, null);
    }

    boolean isReadable(Post post, User viewer, boolean authorBlocked, Set<Long> activeAdminBoardIds) {
        if (post == null || Boolean.TRUE.equals(post.getIsDeleted()) || authorBlocked) {
            return false;
        }

        Board board = post.getBoard();
        if (board == null) {
            return false;
        }
        boolean isAuthor = viewer != null && Objects.equals(post.getUser().getUserId(), viewer.getUserId());
        Boolean hasAdminAccess = null;

        if (!Boolean.TRUE.equals(board.getIsActive())) {
            if (viewer == null) {
                return false;
            }
            if (!isAuthor) {
                hasAdminAccess = resolveAdminAccess(board, viewer, activeAdminBoardIds, hasAdminAccess);
                if (!hasAdminAccess) {
                    return false;
                }
            }
        }

        if (!Boolean.TRUE.equals(board.getIsPublic())) {
            boolean canReadInquiryAsAuthor = boardAccessPolicy.isInquiryBoard(board) && isAuthor;
            if (!canReadInquiryAsAuthor) {
                hasAdminAccess = resolveAdminAccess(board, viewer, activeAdminBoardIds, hasAdminAccess);
            }
            if (!canReadInquiryAsAuthor && !hasAdminAccess) {
                return false;
            }
        }

        if (Boolean.TRUE.equals(post.getIsSecret())
                && !isAuthor) {
            hasAdminAccess = resolveAdminAccess(board, viewer, activeAdminBoardIds, hasAdminAccess);
            if (!hasAdminAccess) {
                return false;
            }
        }
        return true;
    }

    private boolean resolveAdminAccess(Board board, User viewer, Set<Long> activeAdminBoardIds, Boolean resolvedAccess) {
        if (resolvedAccess != null) {
            return resolvedAccess;
        }
        return boardAccessPolicy.hasBoardAdminAccess(board, viewer, activeAdminBoardIds);
    }
}
