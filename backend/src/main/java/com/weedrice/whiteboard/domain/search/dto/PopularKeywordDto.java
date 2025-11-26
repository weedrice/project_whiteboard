package com.weedrice.whiteboard.domain.search.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PopularKeywordDto {
    private final String keyword;
    private final Long count;
}
