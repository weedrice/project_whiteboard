package com.weedrice.whiteboard.domain.point.dto;

import com.weedrice.whiteboard.domain.point.entity.PointHistory;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class PointHistoryResponse {
    private List<PointHistorySummary> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;
    private boolean last;

    @Getter
    @Builder
    public static class PointHistorySummary {
        private Long historyId;
        private String type;
        private int amount;
        private int balanceAfter;
        private String description;
        private LocalDateTime createdAt;
    }

    public static PointHistoryResponse from(Page<PointHistory> historyPage) {
        List<PointHistorySummary> content = historyPage.getContent().stream()
                .map(history -> PointHistorySummary.builder()
                        .historyId(history.getHistoryId())
                        .type(history.getType())
                        .amount(history.getAmount())
                        .balanceAfter(history.getBalanceAfter())
                        .description(history.getDescription())
                        .createdAt(history.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return PointHistoryResponse.builder()
                .content(content)
                .page(historyPage.getNumber())
                .size(historyPage.getSize())
                .totalElements(historyPage.getTotalElements())
                .totalPages(historyPage.getTotalPages())
                .hasNext(historyPage.hasNext())
                .hasPrevious(historyPage.hasPrevious())
                .last(historyPage.isLast())
                .build();
    }
}
