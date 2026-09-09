package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.actor.ActorUserPrincipal;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.board.service.BoardCategoryWritePolicy;
import com.weedrice.whiteboard.domain.board.service.BoardDefaultCategoryResolver;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class PostAuthorCommandPolicy {
    private final BoardAccessPolicy boardAccessPolicy;
    private final BoardCategoryRepository boardCategoryRepository;
    private final BoardCategoryWritePolicy boardCategoryWritePolicy;

    public void validateAuthorCommand(Post post, ActorUserPrincipal user) {
        if (post.getIsDeleted()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        if (!post.getUserId().equals(user.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    public void validateWritableCommand(Post post, ActorUserPrincipal user, BoardCategory category) {
        validateBoardWritable(post.getBoard(), user);
        validateAppliedCategoryWriteRole(post.getBoard(), user, category);
    }

    public void validateDeletable(Post post, ActorUserPrincipal user) {
        validateAuthorCommand(post, user);
    }

    public void validateBoardWritable(Board board, ActorUserPrincipal user) {
        boardAccessPolicy.validateWritable(board, user);
    }

    public boolean canWriteBoardWithDefaultCategory(
            Board board, ActorUserPrincipal user, Set<Long> activeAdminBoardIds) {
        if (!boardAccessPolicy.canWriteBoard(board, user, activeAdminBoardIds)) {
            return false;
        }
        return BoardDefaultCategoryResolver.resolveDefaultCategory(
                        boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(
                                board.getBoardId(),
                                true))
                .map(defaultCategory -> canWriteRole(
                        board,
                        user,
                        defaultCategory.getMinWriteRole(),
                        activeAdminBoardIds))
                .orElse(true);
    }

    public boolean canWriteBoardWithRole(Board board, ActorUserPrincipal user, String minWriteRole,
            Set<Long> activeAdminBoardIds) {
        return boardAccessPolicy.canWriteBoard(board, user, activeAdminBoardIds)
                && canWriteRole(board, user, minWriteRole, activeAdminBoardIds);
    }

    public void validateAppliedCategoryWriteRole(Board board, ActorUserPrincipal user, BoardCategory category) {
        if (category != null) {
            validateWriteRole(board, user, category.getMinWriteRole());
            return;
        }
        BoardDefaultCategoryResolver.resolveDefaultCategory(
                        boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(
                                board.getBoardId(),
                                true))
                .ifPresent(defaultCategory -> validateWriteRole(board, user, defaultCategory.getMinWriteRole()));
    }

    public void validateWriteRole(Board board, ActorUserPrincipal user, String minRole) {
        boardCategoryWritePolicy.validateWriteRole(board, user, minRole);
    }

    private boolean canWriteRole(
            Board board, ActorUserPrincipal user, String minRole, Set<Long> activeAdminBoardIds) {
        try {
            return boardCategoryWritePolicy.canWriteResolvedRole(board, user, minRole, activeAdminBoardIds);
        } catch (BusinessException exception) {
            if (ErrorCode.INVALID_INPUT_VALUE.equals(exception.getErrorCode())) {
                return false;
            }
            throw exception;
        }
    }
}
