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

class BoardCategoryService {

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
        return new CategoryResponse(boardCategoryRepository.save(category));
    }

    CategoryResponse updateCategory(Long categoryId, CategoryRequest request) {
        if (categoryId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Category ID cannot be null");
        }
        BoardCategory category = boardCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        SecurityUtils.validateBoardAdminPermission(category.getBoard());

        category.update(request.getName(), request.getSortOrder(), request.getMinWriteRole());
        return new CategoryResponse(category);
    }

    void deleteCategory(Long categoryId) {
        BoardCategory category = boardCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        SecurityUtils.validateBoardAdminPermission(category.getBoard());

        category.deactivate();
    }
}
