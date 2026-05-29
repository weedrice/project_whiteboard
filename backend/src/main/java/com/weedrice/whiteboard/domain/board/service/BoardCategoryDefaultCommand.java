package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import org.springframework.stereotype.Component;

@Component
class BoardCategoryDefaultCommand {

    private final BoardCategoryRepository boardCategoryRepository;

    BoardCategoryDefaultCommand(BoardCategoryRepository boardCategoryRepository) {
        this.boardCategoryRepository = boardCategoryRepository;
    }

    void clearDefaultCategories(Long boardId, Long exceptCategoryId) {
        boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(boardId, true).stream()
                .filter(BoardCategory::isDefaultCategory)
                .filter(category -> exceptCategoryId == null || !exceptCategoryId.equals(category.getCategoryId()))
                .forEach(category -> category.setDefaultCategory(false));
    }
}
