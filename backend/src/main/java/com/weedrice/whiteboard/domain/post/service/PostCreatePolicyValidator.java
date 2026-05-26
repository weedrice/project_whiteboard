package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostCreatePolicyValidator {

    private final BoardAccessPolicy boardAccessPolicy;
    private final PostAuthorCommandPolicy postAuthorCommandPolicy;

    public void validateBoardAndNotice(PostCreateTarget target, PostCreateRequest request) {
        if (!target.boardWritablePrevalidated()) {
            postAuthorCommandPolicy.validateBoardWritable(target.board(), target.user());
        }

        if (request.isNotice() && !boardAccessPolicy.hasBoardAdminAccess(target.board(), target.user())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    public void validateCategory(PostCreateTarget target, PostCreateCategoryTarget categoryTarget) {
        if (!categoryTarget.categoryWriteRolePrevalidated()) {
            postAuthorCommandPolicy.validateAppliedCategoryWriteRole(
                    target.board(),
                    target.user(),
                    categoryTarget.category());
        }
    }
}
