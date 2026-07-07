package com.weedrice.whiteboard.global.log.entity;

import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "error_logs", indexes = {
        @Index(name = "idx_error_logs_created", columnList = "created_at"),
        @Index(name = "idx_error_logs_resolved", columnList = "is_resolved, created_at"),
        @Index(name = "idx_error_logs_type", columnList = "error_type, created_at"),
        @Index(name = "idx_error_logs_code", columnList = "error_code, created_at"),
        @Index(name = "idx_error_logs_status", columnList = "http_status, created_at")
})
public class ErrorLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "error_log_id")
    private Long errorLogId;

    @Column(name = "error_code", length = 20)
    private String errorCode;

    @Column(name = "error_type", length = 100, nullable = false)
    private String errorType;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "message", length = 500, nullable = false)
    private String message;

    @Column(name = "request_uri", length = 500, nullable = false)
    private String requestUri;

    @Column(name = "request_method", length = 10, nullable = false)
    private String requestMethod;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "ip_address", length = 45, nullable = false)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Column(name = "is_resolved", length = 1, nullable = false)
    private String isResolved = "N";

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_memo", length = 500)
    private String resolvedMemo;

    @Builder
    public ErrorLog(String errorCode, String errorType, int httpStatus, String message,
            String requestUri, String requestMethod, Long userId,
            String ipAddress, String userAgent, String stackTrace) {
        this.errorCode = errorCode;
        this.errorType = errorType;
        this.httpStatus = httpStatus;
        this.message = message;
        this.requestUri = requestUri;
        this.requestMethod = requestMethod;
        this.userId = userId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.stackTrace = stackTrace;
        this.isResolved = "N";
    }

    public void resolve(Long adminUserId, String memo, LocalDateTime resolvedAt) {
        this.isResolved = "Y";
        this.resolvedBy = adminUserId;
        this.resolvedAt = resolvedAt;
        this.resolvedMemo = memo;
    }
}
