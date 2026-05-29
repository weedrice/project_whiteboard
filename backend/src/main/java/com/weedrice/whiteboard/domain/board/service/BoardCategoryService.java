package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.dto.CategoryRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
class BoardCategoryService {

    private final BoardCategoryMutationResolver mutationResolver;
    private final BoardCategoryRepository boardCategoryRepository;
    private final BoardCategoryNameConflictPolicy nameConflictPolicy;
    private final BoardCategoryDefaultCommand defaultCommand;
    private final BoardCategoryResponseAssembler responseAssembler;

    BoardCategoryService(BoardCategoryMutationResolver mutationResolver,
                          BoardCategoryRepository boardCategoryRepository,
                          BoardCategoryNameConflictPolicy nameConflictPolicy,
                          BoardCategoryDefaultCommand defaultCommand,
                          BoardCategoryResponseAssembler responseAssembler) {
        this.mutationResolver = mutationResolver;
        this.boardCategoryRepository = boardCategoryRepository;
        this.nameConflictPolicy = nameConflictPolicy;
        this.defaultCommand = defaultCommand;
        this.responseAssembler = responseAssembler;
    }

    CategoryResponse createCategory(String boardUrl, CategoryRequest request, Long userId) {
        boolean requestedDefault = Boolean.TRUE.equals(request.getIsDefault());
        Board board = mutationResolver.resolveBoardForCreate(boardUrl, userId);

        String normalizedName = normalizeCategoryName(request.getName());
        nameConflictPolicy.validateCreatable(board.getBoardId(), normalizedName);
        if (requestedDefault) {
            defaultCommand.clearDefaultCategories(board.getBoardId(), null);
        }

        BoardCategory category = BoardCategory.builder()
                .board(board)
                .name(normalizedName)
                .sortOrder(request.getSortOrder())
                .minWriteRole(request.getMinWriteRole())
                .isDefault(requestedDefault)
                .build();
        try {
            return responseAssembler.toResponse(boardCategoryRepository.saveAndFlush(category));
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
        BoardCategory category = mutationResolver.resolveCategoryForUpdate(categoryId, userId);

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
            defaultCommand.clearDefaultCategories(category.getBoard().getBoardId(), categoryId);
        }

        Boolean nextDefault = request.getIsDefault() != null ? request.getIsDefault() : category.isDefaultCategory();
        category.update(normalizedName, request.getSortOrder(), request.getMinWriteRole(), nextDefault);
        try {
            return responseAssembler.toResponse(boardCategoryRepository.saveAndFlush(category));
        } catch (DataIntegrityViolationException ex) {
            throw nameConflictPolicy.resolveSaveConflict(ex);
        }
    }

    void deleteCategory(Long categoryId, Long userId) {
        BoardCategory category = mutationResolver.resolveCategoryForUpdate(categoryId, userId);

        if (category.isDefaultCategory()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Default category cannot be deleted");
        }

        category.deactivate();
    }

    private String normalizeCategoryName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Category name cannot be blank");
        }
        return name.trim();
    }

}
