package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class BoardAccessPolicy {

    private static final String DEFAULT_INQUIRY_BOARD_URL = "inquiry";

    private final AdminRepository adminRepository;

    public boolean isInquiryBoard(Board board) {
        return board != null
                && board.getBoardUrl() != null
                && DEFAULT_INQUIRY_BOARD_URL.equalsIgnoreCase(board.getBoardUrl());
    }

    public boolean hasBoardAdminAccess(Board board, User user) {
        if (board == null || user == null) {
            return false;
        }
        if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            return true;
        }
        if (board.getCreator() != null && Objects.equals(board.getCreator().getUserId(), user.getUserId())) {
            return true;
        }
        return adminRepository.existsByUserAndBoardAndIsActive(user, board, true);
    }

    public boolean hasElevatedBoardVisibility(User user) {
        if (user == null) {
            return false;
        }
        if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            return true;
        }
        return adminRepository.existsByUserAndIsActive(user, true);
    }

    public boolean canReadBoard(Board board, User user) {
        if (board == null) {
            return false;
        }
        if (!Boolean.TRUE.equals(board.getIsActive()) && !hasBoardAdminAccess(board, user)) {
            return false;
        }
        if (!Boolean.TRUE.equals(board.getIsPublic()) && !hasBoardAdminAccess(board, user)) {
            return false;
        }
        return true;
    }

    public void validateReadable(Board board, User user) {
        if (!canReadBoard(board, user)) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }
    }

    public boolean canWriteBoard(Board board, User user) {
        if (board == null || user == null) {
            return false;
        }
        if (!Boolean.TRUE.equals(board.getIsActive()) && !hasBoardAdminAccess(board, user)) {
            return false;
        }
        if (!Boolean.TRUE.equals(board.getIsPublic())
                && !hasBoardAdminAccess(board, user)
                && !isInquiryBoard(board)) {
            return false;
        }
        return true;
    }

    public void validateWritable(Board board, User user) {
        if (!canWriteBoard(board, user)) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }
    }

    public boolean canViewSecretPosts(Board board, User user) {
        return hasBoardAdminAccess(board, user);
    }
}
