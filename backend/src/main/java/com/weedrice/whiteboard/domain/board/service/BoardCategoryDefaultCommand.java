package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class BoardCategoryDefaultCommand {

    private final BoardCategoryRepository boardCategoryRepository;

    BoardCategoryDefaultCommand(BoardCategoryRepository boardCategoryRepository) {
        this.boardCategoryRepository = boardCategoryRepository;
    }

    void makeRoomForDefault(Long boardId, BoardCategory selected) {
        List<BoardCategory> active = boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(boardId, true);
        if (selected != null && selected.isDefaultCategory() && selected.getSortOrder() == 1
                && active.stream().filter(BoardCategory::isDefaultCategory).count() == 1) {
            return;
        }
        int nextOrder = 2;
        for (BoardCategory category : active) {
            if (selected != null && selected.getCategoryId().equals(category.getCategoryId())) {
                continue;
            }
            if (category.isDefaultCategory()) {
                category.setDefaultCategory(false);
            }
            category.updateSortOrder(nextOrder++);
        }
    }
}
