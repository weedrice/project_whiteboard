package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.dto.CategoryRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
class BoardCategoryService {

    private static final String ACTIVE_CATEGORY_CONSTRAINT = "uq_board_categories_active_name";
    private static final String ORM_ACTIVE_CATEGORY_CONSTRAINT = "uk_board_categories_board_name_active";
    private static final String LEGACY_ACTIVE_CATEGORY_CONSTRAINT = "board_categories_board_id_name_is_active_key";

    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;

    BoardCategoryService(BoardRepository boardRepository, BoardCategoryRepository boardCategoryRepository) {
        this.boardRepository = boardRepository;
        this.boardCategoryRepository = boardCategoryRepository;
    }

    CategoryResponse createCategory(String boardUrl, CategoryRequest request) {
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        SecurityUtils.validateBoardAdminPermission(board);

        BoardCategory category = BoardCategory.builder()
                .board(board)
                .name(request.getName())
                .sortOrder(request.getSortOrder())
                .minWriteRole(request.getMinWriteRole())
                .build();
        try {
            return new CategoryResponse(boardCategoryRepository.saveAndFlush(category));
        } catch (DataIntegrityViolationException ex) {
            throw resolveCategoryConflict(ex);
        }
    }

    CategoryResponse updateCategory(Long categoryId, CategoryRequest request) {
        if (categoryId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Category ID cannot be null");
        }
        BoardCategory category = boardCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        SecurityUtils.validateBoardAdminPermission(category.getBoard());

        category.update(request.getName(), request.getSortOrder(), request.getMinWriteRole());
        try {
            return new CategoryResponse(boardCategoryRepository.saveAndFlush(category));
        } catch (DataIntegrityViolationException ex) {
            throw resolveCategoryConflict(ex);
        }
    }

    void deleteCategory(Long categoryId) {
        BoardCategory category = boardCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        SecurityUtils.validateBoardAdminPermission(category.getBoard());

        category.deactivate();
    }

    private BusinessException resolveCategoryConflict(DataIntegrityViolationException ex) {
        if (containsConstraint(
                ex,
                ACTIVE_CATEGORY_CONSTRAINT,
                ORM_ACTIVE_CATEGORY_CONSTRAINT,
                LEGACY_ACTIVE_CATEGORY_CONSTRAINT)) {
            return new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Duplicate active board category");
        }
        return new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
    }

    private boolean containsConstraint(Throwable throwable, String... candidates) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage() != null ? current.getMessage().toLowerCase() : "";
            if (containsAny(message, candidates)) {
                return true;
            }
            if (current instanceof ConstraintViolationException constraintViolationException) {
                String constraintName = constraintViolationException.getConstraintName();
                if (containsAny(constraintName != null ? constraintName.toLowerCase() : "", candidates)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
