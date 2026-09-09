package com.weedrice.whiteboard.domain.post.scheduled.service;

import com.weedrice.whiteboard.domain.actor.ActorUserPrincipal;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.post.repository.PostSeriesRepository;
import com.weedrice.whiteboard.domain.post.scheduled.dto.ScheduledPostRequest;
import com.weedrice.whiteboard.domain.post.service.PostAuthorCommandPolicy;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ScheduledPostRequestPolicy {

    private final BoardCategoryRepository boardCategoryRepository;
    private final BoardAccessPolicy boardAccessPolicy;
    private final PostAuthorCommandPolicy postAuthorCommandPolicy;
    private final PostSeriesRepository postSeriesRepository;

    void validate(ActorUserPrincipal user, Board board, ScheduledPostRequest request) {
        BoardCategory category = resolveActiveCategory(board, request.getCategoryId());
        postAuthorCommandPolicy.validateAppliedCategoryWriteRole(board, user, category);
        validateNoticePermission(user, board, request.isNotice());
        validateSeriesOwnership(user, request.getSeriesId());
    }

    private BoardCategory resolveActiveCategory(Board board, Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(
                        categoryId,
                        board.getBoardId(),
                        true)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void validateNoticePermission(ActorUserPrincipal user, Board board, boolean notice) {
        if (notice && !boardAccessPolicy.hasBoardAdminAccess(board, user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateSeriesOwnership(ActorUserPrincipal user, Long seriesId) {
        if (seriesId == null) {
            return;
        }
        postSeriesRepository.findBySeriesIdAndOwnerUserId(seriesId, user.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
