package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.board.service.BoardCategoryWritePolicy;
import com.weedrice.whiteboard.domain.board.service.BoardDefaultCategoryResolver;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostAuthorCommandPolicy {
    private final BoardAccessPolicy boardAccessPolicy;
    private final BoardCategoryRepository boardCategoryRepository;
    private final BoardCategoryWritePolicy boardCategoryWritePolicy;

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
    }

    public void validateBoardWritable(Board board, User user) {
        boardAccessPolicy.validateWritable(board, user);
    }

    public void validateAppliedCategoryWriteRole(Board board, User user, BoardCategory category) {
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

    public void validateWriteRole(Board board, User user, String minRole) {
        boardCategoryWritePolicy.validateWriteRole(board, user, minRole);
    }
}
