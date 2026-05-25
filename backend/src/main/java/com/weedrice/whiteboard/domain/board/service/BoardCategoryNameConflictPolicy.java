package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class BoardCategoryNameConflictPolicy {

    private final BoardCategoryRepository boardCategoryRepository;

    void validateCreatable(Long boardId, String name) {
        if (boardCategoryRepository.existsByBoard_BoardIdAndNameAndIsActive(boardId, name, true)) {
            throw duplicateActiveName();
        }
    }

    void validateUpdatable(Long boardId, String name, Long categoryId) {
        if (boardCategoryRepository.existsByBoard_BoardIdAndNameAndIsActiveAndCategoryIdNot(
                boardId,
                name,
                true,
                categoryId)) {
            throw duplicateActiveName();
        }
    }

    BusinessException resolveSaveConflict(DataIntegrityViolationException ex) {
        if (BoardCategoryConstraintResolver.isActiveNameConstraint(ex)) {
            return duplicateActiveName();
        }
        return new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
    }

    private BusinessException duplicateActiveName() {
        return new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Duplicate active board category");
    }
}
