package com.weedrice.whiteboard.global.log.dto;

import com.weedrice.whiteboard.global.log.entity.Log;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class LogResponse {
    private List<LogSummary> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    @Getter
    @Builder
    public static class LogSummary {
        private Long logId;
        private Long userId;
        private String actionType;
        private String ipAddress;
        private String details;
        private LocalDateTime createdAt;
    }

    public static LogResponse from(Page<Log> logPage) {
        List<LogSummary> content = logPage.getContent().stream()
                .map(log -> LogSummary.builder()
                        .logId(log.getLogId())
                        .userId(log.getUserId())
                        .actionType(log.getActionType())
                        .ipAddress(log.getIpAddress())
                        .details(log.getDetails())
                        .createdAt(log.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return LogResponse.builder()
                .content(content)
                .page(logPage.getNumber())
                .size(logPage.getSize())
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .hasNext(logPage.hasNext())
                .hasPrevious(logPage.hasPrevious())
                .build();
    }
}
