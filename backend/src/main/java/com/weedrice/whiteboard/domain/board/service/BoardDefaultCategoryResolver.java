package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class BoardDefaultCategoryResolver {

    private BoardDefaultCategoryResolver() {
    }

    public static Optional<BoardCategory> resolveDefaultCategory(List<BoardCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            return Optional.empty();
        }
        return categories.stream()
                .filter(BoardCategory::isDefaultCategory)
                .min(Comparator.comparing(BoardCategory::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(BoardCategory::getCategoryId, Comparator.nullsLast(Long::compareTo)))
                .or(() -> categories.stream()
                        .min(Comparator.comparing(BoardCategory::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                                .thenComparing(BoardCategory::getCategoryId, Comparator.nullsLast(Long::compareTo))));
    }

    public static Optional<CategoryResponse> resolveDefaultCategoryResponse(List<CategoryResponse> categories) {
        if (categories == null || categories.isEmpty()) {
            return Optional.empty();
        }
        return categories.stream()
                .filter(CategoryResponse::isDefault)
                .min(Comparator.comparing(CategoryResponse::getSortOrder)
                        .thenComparing(CategoryResponse::getCategoryId, Comparator.nullsLast(Long::compareTo)))
                .or(() -> categories.stream()
                        .min(Comparator.comparing(CategoryResponse::getSortOrder)
                                .thenComparing(CategoryResponse::getCategoryId, Comparator.nullsLast(Long::compareTo))));
    }
}
