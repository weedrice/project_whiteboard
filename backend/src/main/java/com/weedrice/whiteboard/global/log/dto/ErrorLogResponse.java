package com.weedrice.whiteboard.global.log.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ErrorLogResponse {
    private List<ErrorLogSummary> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    @Getter
    @Builder
    public static class ErrorLogSummary {
        private Long errorLogId;
        private String errorCode;
        private String errorType;
        private int httpStatus;
        private String message;
        private String requestUri;
        private String requestMethod;
        private Long userId;
        private String ipAddress;
        private String userAgent;
        private String isResolved;
        private Long resolvedBy;
        private LocalDateTime resolvedAt;
        private String resolvedMemo;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class ErrorLogDetail {
        private Long errorLogId;
        private String errorCode;
        private String errorType;
        private int httpStatus;
        private String message;
        private String requestUri;
        private String requestMethod;
        private Long userId;
        private String ipAddress;
        private String userAgent;
        private String stackTrace;
        private String isResolved;
        private Long resolvedBy;
        private LocalDateTime resolvedAt;
        private String resolvedMemo;
        private LocalDateTime createdAt;
    }

    public static ErrorLogResponse from(Page<ErrorLogSummary> errorLogPage) {
        return ErrorLogResponse.builder()
                .content(errorLogPage.getContent())
                .page(errorLogPage.getNumber())
                .size(errorLogPage.getSize())
                .totalElements(errorLogPage.getTotalElements())
                .totalPages(errorLogPage.getTotalPages())
                .hasNext(errorLogPage.hasNext())
                .hasPrevious(errorLogPage.hasPrevious())
                .build();
    }
}
