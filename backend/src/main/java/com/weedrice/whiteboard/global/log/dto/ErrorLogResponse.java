package com.weedrice.whiteboard.global.log.dto;

import com.weedrice.whiteboard.global.log.entity.ErrorLog;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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
        private String stackTrace;
        private String isResolved;
        private Long resolvedBy;
        private LocalDateTime resolvedAt;
        private String resolvedMemo;
        private LocalDateTime createdAt;
    }

    public static ErrorLogResponse from(Page<ErrorLog> errorLogPage) {
        List<ErrorLogSummary> content = errorLogPage.getContent().stream()
                .map(log -> ErrorLogSummary.builder()
                        .errorLogId(log.getErrorLogId())
                        .errorCode(log.getErrorCode())
                        .errorType(log.getErrorType())
                        .httpStatus(log.getHttpStatus())
                        .message(log.getMessage())
                        .requestUri(log.getRequestUri())
                        .requestMethod(log.getRequestMethod())
                        .userId(log.getUserId())
                        .ipAddress(log.getIpAddress())
                        .userAgent(log.getUserAgent())
                        .stackTrace(log.getStackTrace())
                        .isResolved(log.getIsResolved())
                        .resolvedBy(log.getResolvedBy())
                        .resolvedAt(log.getResolvedAt())
                        .resolvedMemo(log.getResolvedMemo())
                        .createdAt(log.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return ErrorLogResponse.builder()
                .content(content)
                .page(errorLogPage.getNumber())
                .size(errorLogPage.getSize())
                .totalElements(errorLogPage.getTotalElements())
                .totalPages(errorLogPage.getTotalPages())
                .hasNext(errorLogPage.hasNext())
                .hasPrevious(errorLogPage.hasPrevious())
                .build();
    }
}
