package com.weedrice.whiteboard.global.log.controller;

import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.log.dto.*;
import com.weedrice.whiteboard.global.log.service.ErrorLogService;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.weedrice.whiteboard.domain.user.entity.Role;

/**
 * 에러 로그 관리 컨트롤러 (관리자 전용)
 */
@RestController
@RequestMapping("/api/v1/admin/error-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
public class ErrorLogController {

    private final ErrorLogService errorLogService;

    /**
     * 에러 로그 목록 조회 (페이징 + 필터)
     */
    @GetMapping
    public ApiResponse<ErrorLogResponse> getErrorLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            ErrorLogSearchRequest searchRequest) {
        Pageable pageable = PageRequestUtils.of(page, size, Sort.by("createdAt").descending());
        return ApiResponse.success(ErrorLogResponse.from(errorLogService.getErrorLogs(searchRequest, pageable)));
    }

    /**
     * 에러 로그 상세 조회
     */
    @GetMapping("/{errorLogId}")
    public ApiResponse<ErrorLogResponse.ErrorLogSummary> getErrorLog(@PathVariable Long errorLogId) {
        var errorLog = errorLogService.getErrorLog(errorLogId);
        return ApiResponse.success(ErrorLogResponse.ErrorLogSummary.builder()
                .errorLogId(errorLog.getErrorLogId())
                .errorCode(errorLog.getErrorCode())
                .errorType(errorLog.getErrorType())
                .httpStatus(errorLog.getHttpStatus())
                .message(errorLog.getMessage())
                .requestUri(errorLog.getRequestUri())
                .requestMethod(errorLog.getRequestMethod())
                .userId(errorLog.getUserId())
                .ipAddress(errorLog.getIpAddress())
                .userAgent(errorLog.getUserAgent())
                .stackTrace(errorLog.getStackTrace())
                .isResolved(errorLog.getIsResolved())
                .resolvedBy(errorLog.getResolvedBy())
                .resolvedAt(errorLog.getResolvedAt())
                .resolvedMemo(errorLog.getResolvedMemo())
                .createdAt(errorLog.getCreatedAt())
                .build());
    }

    /**
     * 에러 로그 확인 처리
     */
    @PutMapping("/{errorLogId}/resolve")
    public ApiResponse<Void> resolveErrorLog(
            @PathVariable Long errorLogId,
            @RequestBody(required = false) ErrorLogResolveRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String memo = request != null ? request.getMemo() : null;
        errorLogService.resolveErrorLog(errorLogId, userDetails.getUserId(), memo);
        return ApiResponse.success(null);
    }

    /**
     * 에러 로그 통계 조회
     */
    @GetMapping("/stats")
    public ApiResponse<ErrorLogStatsResponse> getErrorLogStats() {
        return ApiResponse.success(errorLogService.getErrorLogStats());
    }
}
