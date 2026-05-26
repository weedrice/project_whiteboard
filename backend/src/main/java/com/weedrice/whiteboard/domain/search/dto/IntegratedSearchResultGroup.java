package com.weedrice.whiteboard.domain.search.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class IntegratedSearchResultGroup<T> {
    private final List<T> items;
    private final long totalElements;
    private final int totalPages;
    private final int page;
    private final int size;
    private final boolean hasMore;

    public static <T> IntegratedSearchResultGroup<T> from(Page<T> page) {
        return IntegratedSearchResultGroup.<T>builder()
                .items(page.getContent())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .page(page.getNumber())
                .size(page.getSize())
                .hasMore(!page.isLast())
                .build();
    }
}
