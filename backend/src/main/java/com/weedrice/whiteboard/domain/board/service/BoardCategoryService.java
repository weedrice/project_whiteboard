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
        boolean requestedDefault = Boolean.TRUE.equals(request.getIsDefault());
        Board board = findBoardForCategoryCreate(boardUrl);

        SecurityUtils.validateBoardAdminPermission(board);
        String normalizedName = normalizeCategoryName(request.getName());
        validateDuplicateActiveName(board.getBoardId(), normalizedName);
        if (requestedDefault) {
            clearDefaultCategories(board.getBoardId(), null);
        }

        BoardCategory category = BoardCategory.builder()
                .board(board)
                .name(normalizedName)
                .sortOrder(request.getSortOrder())
                .minWriteRole(request.getMinWriteRole())
                .isDefault(requestedDefault)
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
        BoardCategory category = findCategoryForUpdate(categoryId);

        SecurityUtils.validateBoardAdminPermission(category.getBoard());
        String normalizedName = normalizeCategoryName(request.getName());
        if (Boolean.TRUE.equals(category.getIsActive())) {
            validateDuplicateActiveName(category.getBoard().getBoardId(), normalizedName, categoryId);
        }
        if (!Boolean.TRUE.equals(category.getIsActive()) && Boolean.TRUE.equals(request.getIsDefault())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Inactive category cannot be default");
        }
        if (category.isDefaultCategory() && Boolean.FALSE.equals(request.getIsDefault())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Default category cannot be unset directly");
        }
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefaultCategories(category.getBoard().getBoardId(), categoryId);
        }

        Boolean nextDefault = request.getIsDefault() != null ? request.getIsDefault() : category.isDefaultCategory();
        category.update(normalizedName, request.getSortOrder(), request.getMinWriteRole(), nextDefault);
        try {
            return new CategoryResponse(boardCategoryRepository.saveAndFlush(category));
        } catch (DataIntegrityViolationException ex) {
            throw resolveCategoryConflict(ex);
        }
    }

    void deleteCategory(Long categoryId) {
        BoardCategory category = findCategoryForUpdate(categoryId);

        SecurityUtils.validateBoardAdminPermission(category.getBoard());
        if (category.isDefaultCategory()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Default category cannot be deleted");
        }

        category.deactivate();
    }

    private Board findBoardForCategoryCreate(String boardUrl) {
        return boardRepository.findByBoardUrlForUpdate(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
    }

    private BoardCategory findCategoryForUpdate(Long categoryId) {
        Long boardId = boardCategoryRepository.findBoardIdByCategoryId(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        boardRepository.findByIdForUpdate(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        return boardCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void clearDefaultCategories(Long boardId, Long exceptCategoryId) {
        boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(boardId, true).stream()
                .filter(BoardCategory::isDefaultCategory)
                .filter(category -> exceptCategoryId == null || !exceptCategoryId.equals(category.getCategoryId()))
                .forEach(category -> category.setDefaultCategory(false));
    }

    private String normalizeCategoryName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Category name cannot be blank");
        }
        return name.trim();
    }

    private void validateDuplicateActiveName(Long boardId, String name) {
        if (boardCategoryRepository.existsByBoard_BoardIdAndNameAndIsActive(boardId, name, true)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Duplicate active board category");
        }
    }

    private void validateDuplicateActiveName(Long boardId, String name, Long categoryId) {
        if (boardCategoryRepository.existsByBoard_BoardIdAndNameAndIsActiveAndCategoryIdNot(
                boardId,
                name,
                true,
                categoryId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Duplicate active board category");
        }
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
