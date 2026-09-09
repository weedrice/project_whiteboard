package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.actor.ActorUserPrincipal;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class BoardAccessPolicy {

    private final AdminRepository adminRepository;

    public boolean isInquiryBoardUrl(String boardUrl) {
        return boardUrl != null && BoardPolicyConstants.INQUIRY_BOARD_URL.equalsIgnoreCase(boardUrl.trim());
    }

    public String getInquiryBoardUrl() {
        return BoardPolicyConstants.INQUIRY_BOARD_URL;
    }

    public boolean isInquiryBoard(Board board) {
        return board != null && isInquiryBoardUrl(board.getBoardUrl());
    }

    public boolean hasBoardAdminAccess(Board board, ActorUserPrincipal user) {
        if (board == null || user == null) {
            return false;
        }
        if (hasStaticBoardAdminAccess(user)) {
            return true;
        }
        return adminRepository.existsByUser_UserIdAndBoard_BoardIdAndIsActive(
                user.getUserId(), board.getBoardId(), true);
    }

    public void validateBoardAdmin(Board board, ActorUserPrincipal user) {
        if (!hasBoardAdminAccess(board, user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    public boolean hasBoardAdminAccess(Board board, ActorUserPrincipal user, Set<Long> activeAdminBoardIds) {
        if (board == null || user == null) {
            return false;
        }
        if (hasStaticBoardAdminAccess(user)) {
            return true;
        }
        if (activeAdminBoardIds == null) {
            return hasBoardAdminAccess(board, user);
        }
        return activeAdminBoardIds.contains(board.getBoardId());
    }

    private boolean hasStaticBoardAdminAccess(ActorUserPrincipal user) {
        return user.isUsableSuperAdmin();
    }

    public boolean hasElevatedBoardVisibility(ActorUserPrincipal user) {
        if (user == null) {
            return false;
        }
        if (user.isUsableSuperAdmin()) {
            return true;
        }
        return adminRepository.existsByUser_UserIdAndIsActive(user.getUserId(), true);
    }

    public boolean canReadBoard(Board board, ActorUserPrincipal user) {
        return canReadBoard(board, user, null);
    }

    public boolean canReadBoard(Board board, ActorUserPrincipal user, Set<Long> activeAdminBoardIds) {
        if (board == null) {
            return false;
        }
        if (Boolean.TRUE.equals(board.getIsActive()) && Boolean.TRUE.equals(board.getIsPublic())) {
            return true;
        }

        boolean hasAdminAccess = hasBoardAdminAccess(board, user, activeAdminBoardIds);
        if (!Boolean.TRUE.equals(board.getIsActive()) && !hasAdminAccess) {
            return false;
        }
        if (!Boolean.TRUE.equals(board.getIsPublic()) && !hasAdminAccess) {
            return false;
        }
        return true;
    }

    public void validateReadable(Board board, ActorUserPrincipal user) {
        if (!canReadBoard(board, user)) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }
    }

    public boolean canWriteBoard(Board board, ActorUserPrincipal user) {
        return canWriteBoard(board, user, null);
    }

    public boolean canWriteBoard(Board board, ActorUserPrincipal user, Set<Long> activeAdminBoardIds) {
        if (board == null || user == null) {
            return false;
        }
        if (Boolean.TRUE.equals(board.getIsActive())
                && (Boolean.TRUE.equals(board.getIsPublic()) || isInquiryBoard(board))) {
            return true;
        }

        boolean hasAdminAccess = hasBoardAdminAccess(board, user, activeAdminBoardIds);
        if (!Boolean.TRUE.equals(board.getIsActive()) && !hasAdminAccess) {
            return false;
        }
        if (!Boolean.TRUE.equals(board.getIsPublic())
                && !hasAdminAccess
                && !isInquiryBoard(board)) {
            return false;
        }
        return true;
    }

    public void validateWritable(Board board, ActorUserPrincipal user) {
        if (!canWriteBoard(board, user)) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }
    }

    public boolean canViewSecretPosts(Board board, ActorUserPrincipal user) {
        return hasBoardAdminAccess(board, user);
    }
}
