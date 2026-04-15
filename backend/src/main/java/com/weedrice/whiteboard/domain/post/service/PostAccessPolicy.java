package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class PostAccessPolicy {

    private static final String DEFAULT_INQUIRY_BOARD_URL = "inquiry";

    private final AdminRepository adminRepository;

    public void validateReadable(Post post, User viewer) {
        validateReadable(post, viewer, false);
    }

    public void validateReadable(Post post, User viewer, boolean authorBlocked) {
        if (post == null || Boolean.TRUE.equals(post.getIsDeleted()) || authorBlocked) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        Board board = post.getBoard();
        boolean isAuthor = viewer != null && Objects.equals(post.getUser().getUserId(), viewer.getUserId());

        if (!Boolean.TRUE.equals(board.getIsActive())
                && (viewer == null || (!hasBoardAdminAccess(board, viewer) && !isAuthor))) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        if (!Boolean.TRUE.equals(board.getIsPublic())) {
            boolean canReadInquiryAsAuthor = isInquiryBoard(board) && isAuthor;
            if (!hasBoardAdminAccess(board, viewer) && !canReadInquiryAsAuthor) {
                throw new BusinessException(ErrorCode.POST_NOT_FOUND);
            }
        }

        if (Boolean.TRUE.equals(post.getIsSecret()) && !hasBoardAdminAccess(board, viewer) && !isAuthor) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
    }

    private boolean hasBoardAdminAccess(Board board, User user) {
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

    private boolean isInquiryBoard(Board board) {
        return board != null
                && board.getBoardUrl() != null
                && DEFAULT_INQUIRY_BOARD_URL.equalsIgnoreCase(board.getBoardUrl());
    }
}
