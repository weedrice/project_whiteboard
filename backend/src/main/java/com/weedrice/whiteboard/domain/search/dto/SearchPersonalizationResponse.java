package com.weedrice.whiteboard.domain.search.dto;

import com.weedrice.whiteboard.domain.search.entity.SearchPersonalization;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class SearchPersonalizationResponse {
    private List<SearchLog> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    @Getter
    @Builder
    public static class SearchLog {
        private Long logId;
        private String keyword;
        private LocalDateTime searchedAt;
    }

    public static SearchPersonalizationResponse from(Page<SearchPersonalization> personalizationPage) {
        List<SearchLog> content = personalizationPage.getContent().stream()
                .map(log -> SearchLog.builder()
                        .logId(log.getLogId())
                        .keyword(log.getKeyword())
                        .searchedAt(log.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return SearchPersonalizationResponse.builder()
                .content(content)
                .page(personalizationPage.getNumber())
                .size(personalizationPage.getSize())
                .totalElements(personalizationPage.getTotalElements())
                .totalPages(personalizationPage.getTotalPages())
                .hasNext(personalizationPage.hasNext())
                .hasPrevious(personalizationPage.hasPrevious())
                .build();
    }
}
