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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
            throw BusinessException.withMessageKey(ErrorCode.VALIDATION_ERROR, "validation.category.id.required");
        }
        if (request.getSortOrder() == null) {
            throw BusinessException.withMessageKey(ErrorCode.VALIDATION_ERROR, "validation.category.sortOrder.required");
        }
        BoardCategory category = mutationResolver.resolveCategoryForUpdate(categoryId, userId);

        String normalizedName = normalizeCategoryName(request.getName());
        if (Boolean.TRUE.equals(category.getIsActive())) {
            nameConflictPolicy.validateUpdatable(category.getBoard().getBoardId(), normalizedName, categoryId);
        }
        if (!Boolean.TRUE.equals(category.getIsActive()) && Boolean.TRUE.equals(request.getIsDefault())) {
            throw BusinessException.withMessageKey(ErrorCode.VALIDATION_ERROR, "validation.category.inactiveDefault");
        }
        if (category.isDefaultCategory() && Boolean.FALSE.equals(request.getIsDefault())) {
            throw BusinessException.withMessageKey(ErrorCode.VALIDATION_ERROR, "validation.category.defaultUnset");
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
            throw BusinessException.withMessageKey(ErrorCode.VALIDATION_ERROR, "validation.category.defaultDelete");
        }

        category.deactivate();
    }

    List<CategoryResponse> reorderCategories(String boardUrl, List<Long> categoryIds, Long userId) {
        Board board = mutationResolver.resolveBoardForUpdate(boardUrl, userId);
        List<BoardCategory> activeCategories = boardCategoryRepository
                .findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(), true);
        validateCompleteOrder(activeCategories, categoryIds);

        Map<Long, BoardCategory> categoriesById = activeCategories.stream()
                .collect(Collectors.toMap(BoardCategory::getCategoryId, Function.identity()));
        for (int index = 0; index < categoryIds.size(); index++) {
            categoriesById.get(categoryIds.get(index)).updateSortOrder(index + 1);
        }

        return categoryIds.stream()
                .map(categoriesById::get)
                .map(responseAssembler::toResponse)
                .toList();
    }

    private void validateCompleteOrder(List<BoardCategory> activeCategories, List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty() || categoryIds.size() != activeCategories.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        Set<Long> requestedIds = new HashSet<>(categoryIds);
        Set<Long> activeIds = activeCategories.stream()
                .map(BoardCategory::getCategoryId)
                .collect(Collectors.toSet());
        if (requestedIds.size() != categoryIds.size() || !requestedIds.equals(activeIds)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        boolean everyDefaultCategoryIsFirst = activeCategories.stream()
                .filter(BoardCategory::isDefaultCategory)
                .allMatch(defaultCategory -> defaultCategory.getCategoryId().equals(categoryIds.getFirst()));
        if (!everyDefaultCategoryIsFirst) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private String normalizeCategoryName(String name) {
        if (name == null || name.isBlank()) {
            throw BusinessException.withMessageKey(ErrorCode.VALIDATION_ERROR, "validation.category.name.required");
        }
        return name.trim();
    }

}
