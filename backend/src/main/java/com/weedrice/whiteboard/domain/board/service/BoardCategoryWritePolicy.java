package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class BoardCategoryWritePolicy {

    private final BoardAccessPolicy boardAccessPolicy;

    public void validateWriteRole(Board board, User user, String minWriteRole) {
        if (!canWriteResolvedRole(board, user, minWriteRole)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    public boolean canWriteResolvedRole(Board board, User user, String minWriteRole) {
        return canWriteRole(board, user, BoardCategory.resolveMinWriteRole(minWriteRole), null);
    }

    public boolean canWriteResolvedRole(Board board, User user, String minWriteRole, Set<Long> activeAdminBoardIds) {
        return canWriteRole(board, user, BoardCategory.resolveMinWriteRole(minWriteRole), activeAdminBoardIds);
    }

    public boolean canWriteLenientRole(Board board, User user, String minWriteRole, Set<Long> activeAdminBoardIds) {
        if (Role.SUPER_ADMIN.equals(minWriteRole)) {
            return user != null && user.isUsableSuperAdmin();
        }
        if (Role.BOARD_ADMIN.equals(minWriteRole)) {
            return boardAccessPolicy.hasBoardAdminAccess(board, user, activeAdminBoardIds);
        }
        return true;
    }

    private boolean canWriteRole(Board board, User user, String resolvedMinWriteRole, Set<Long> activeAdminBoardIds) {
        return switch (resolvedMinWriteRole) {
            case Role.USER -> true;
            case Role.BOARD_ADMIN -> boardAccessPolicy.hasBoardAdminAccess(board, user, activeAdminBoardIds);
            case Role.SUPER_ADMIN -> user != null && user.isUsableSuperAdmin();
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        };
    }
}
