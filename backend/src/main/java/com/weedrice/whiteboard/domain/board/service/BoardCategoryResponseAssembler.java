package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import org.springframework.stereotype.Component;

@Component
class BoardCategoryResponseAssembler {

    CategoryResponse toResponse(BoardCategory category) {
        return new CategoryResponse(category);
    }
}
