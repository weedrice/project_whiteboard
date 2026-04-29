package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostAuthorCommandPolicy {
    private static final String DEFAULT_CATEGORY_NAME = "\uC77C\uBC18";

    private final BoardAccessPolicy boardAccessPolicy;
    private final BoardCategoryRepository boardCategoryRepository;

    public void validateAuthorCommand(Post post, User user) {
        if (post.getIsDeleted()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        if (!post.getUser().getUserId().equals(user.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    public void validateWritableCommand(Post post, User user, BoardCategory category) {
        validateBoardWritable(post.getBoard(), user);
        validateAppliedCategoryWriteRole(post.getBoard(), user, category);
    }

    public void validateDeletable(Post post, User user) {
        validateAuthorCommand(post, user);
        validateWritableCommand(post, user, post.getCategory());
    }

    public void validateBoardWritable(Board board, User user) {
        boardAccessPolicy.validateWritable(board, user);
    }

    public void validateAppliedCategoryWriteRole(Board board, User user, BoardCategory category) {
        if (category != null) {
            validateWriteRole(board, user, category.getMinWriteRole());
            return;
        }
        boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(), true).stream()
                .filter(defaultCategory -> DEFAULT_CATEGORY_NAME.equals(defaultCategory.getName()))
                .findFirst()
                .ifPresent(defaultCategory -> validateWriteRole(board, user, defaultCategory.getMinWriteRole()));
    }

    public void validateWriteRole(Board board, User user, String minRole) {
        String normalizedMinRole = BoardCategory.resolveMinWriteRole(minRole);
        switch (normalizedMinRole) {
            case Role.USER:
                return;
            case Role.BOARD_ADMIN:
                if (!boardAccessPolicy.hasBoardAdminAccess(board, user)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                }
                return;
            case Role.SUPER_ADMIN:
                if (!Boolean.TRUE.equals(user.getIsSuperAdmin())) {
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                }
                return;
            default:
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
