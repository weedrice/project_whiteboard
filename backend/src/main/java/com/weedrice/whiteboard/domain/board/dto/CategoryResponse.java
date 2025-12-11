package com.weedrice.whiteboard.domain.board.dto;

import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import lombok.Getter;

@Getter
public class CategoryResponse {
    private final Long categoryId;
    private final String name;
    private final int sortOrder;
    private final String minWriteRole;

    public CategoryResponse(BoardCategory category) {
        this.categoryId = category.getCategoryId();
        this.name = category.getName();
        this.sortOrder = category.getSortOrder();
        this.minWriteRole = category.getMinWriteRole();
    }
}
