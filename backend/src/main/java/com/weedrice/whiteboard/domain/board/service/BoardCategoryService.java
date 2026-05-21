package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.dto.CategoryRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
class BoardCategoryService {

    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final UserRepository userRepository;
    private final BoardAccessPolicy boardAccessPolicy;
    private final BoardCategoryNameConflictPolicy nameConflictPolicy;

    BoardCategoryService(BoardRepository boardRepository,
                          BoardCategoryRepository boardCategoryRepository,
                          UserRepository userRepository,
                          BoardAccessPolicy boardAccessPolicy,
                          BoardCategoryNameConflictPolicy nameConflictPolicy) {
        this.boardRepository = boardRepository;
        this.boardCategoryRepository = boardCategoryRepository;
        this.userRepository = userRepository;
        this.boardAccessPolicy = boardAccessPolicy;
        this.nameConflictPolicy = nameConflictPolicy;
    }

    CategoryResponse createCategory(String boardUrl, CategoryRequest request, Long userId) {
        boolean requestedDefault = Boolean.TRUE.equals(request.getIsDefault());
        Board board = findBoardForCategoryCreate(boardUrl);

        User currentUser = getCurrentUser(userId);
        boardAccessPolicy.validateBoardAdmin(board, currentUser);
        String normalizedName = normalizeCategoryName(request.getName());
        nameConflictPolicy.validateCreatable(board.getBoardId(), normalizedName);
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
            throw nameConflictPolicy.resolveSaveConflict(ex);
        }
    }

    CategoryResponse updateCategory(Long categoryId, CategoryRequest request, Long userId) {
        if (categoryId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Category ID cannot be null");
        }
        if (request.getSortOrder() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Category sortOrder cannot be null");
        }
        BoardCategory category = findCategoryForUpdate(categoryId);

        User currentUser = getCurrentUser(userId);
        boardAccessPolicy.validateBoardAdmin(category.getBoard(), currentUser);
        String normalizedName = normalizeCategoryName(request.getName());
        if (Boolean.TRUE.equals(category.getIsActive())) {
            nameConflictPolicy.validateUpdatable(category.getBoard().getBoardId(), normalizedName, categoryId);
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
            throw nameConflictPolicy.resolveSaveConflict(ex);
        }
    }

    void deleteCategory(Long categoryId, Long userId) {
        BoardCategory category = findCategoryForUpdate(categoryId);

        User currentUser = getCurrentUser(userId);
        boardAccessPolicy.validateBoardAdmin(category.getBoard(), currentUser);
        if (category.isDefaultCategory()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Default category cannot be deleted");
        }

        category.deactivate();
    }

    private User getCurrentUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
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

}
