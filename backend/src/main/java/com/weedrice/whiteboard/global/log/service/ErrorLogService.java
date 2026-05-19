package com.weedrice.whiteboard.global.log.service;

import com.weedrice.whiteboard.global.common.util.ClientMetadataNormalizer;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.log.dto.ErrorLogSearchRequest;
import com.weedrice.whiteboard.global.log.dto.ErrorLogResponse;
import com.weedrice.whiteboard.global.log.dto.ErrorLogStatsResponse;
import com.weedrice.whiteboard.global.log.entity.ErrorLog;
import com.weedrice.whiteboard.global.log.repository.ErrorLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

@Service
@RequiredArgsConstructor
public class ErrorLogService {
    private static final int MAX_TEXT_LENGTH = 500;
    private static final int MAX_RESOLVED_MEMO_LENGTH = 500;

    private final ErrorLogRepository errorLogRepository;

    /**
     * 에러 로그 비동기 저장
     * 별도 트랜잭션으로 실행하여 원래 요청의 트랜잭션에 영향을 주지 않음
     */
    @Async("taskExecutor")
    @Transactional(propagation = REQUIRES_NEW)
    public void saveErrorLog(ErrorLog errorLog) {
        errorLogRepository.save(buildErrorLog(
                errorLog.getErrorCode(),
                errorLog.getErrorType(),
                errorLog.getHttpStatus(),
                errorLog.getMessage(),
                errorLog.getRequestUri(),
                errorLog.getRequestMethod(),
                errorLog.getUserId(),
                errorLog.getIpAddress(),
                errorLog.getUserAgent(),
                errorLog.getStackTrace()));
    }

    /**
     * 에러 로그 비동기 저장 (빌더 파라미터)
     */
    @Async("taskExecutor")
    @Transactional(propagation = REQUIRES_NEW)
    public void saveErrorLog(String errorCode, String errorType, int httpStatus, String message,
            String requestUri, String requestMethod, Long userId,
            String ipAddress, String userAgent, String stackTrace) {
        errorLogRepository.save(buildErrorLog(
                errorCode, errorType, httpStatus, message, requestUri, requestMethod, userId,
                ipAddress, userAgent, stackTrace));
    }

    private ErrorLog buildErrorLog(String errorCode, String errorType, int httpStatus, String message,
            String requestUri, String requestMethod, Long userId,
            String ipAddress, String userAgent, String stackTrace) {
        return ErrorLog.builder()
                .errorCode(errorCode)
                .errorType(errorType)
                .httpStatus(httpStatus)
                .message(truncate(message, MAX_TEXT_LENGTH))
                .requestUri(truncate(requestUri, MAX_TEXT_LENGTH))
                .requestMethod(requestMethod)
                .userId(userId)
                .ipAddress(ClientMetadataNormalizer.normalizeIpAddress(ipAddress))
                .userAgent(truncate(userAgent, MAX_TEXT_LENGTH))
                .stackTrace(stackTrace)
                .build();
    }

    /**
     * 에러 로그 검색 조회 (관리자용)
     */
    @Transactional(readOnly = true)
    public Page<ErrorLogResponse.ErrorLogSummary> getErrorLogs(ErrorLogSearchRequest condition, Pageable pageable) {
        SecurityUtils.validateSuperAdminPermission();
        return errorLogRepository.searchErrorLogs(condition, pageable);
    }

    /**
     * 에러 로그 상세 조회 (관리자용)
     */
    @Transactional(readOnly = true)
    public ErrorLog getErrorLog(Long errorLogId) {
        SecurityUtils.validateSuperAdminPermission();
        return errorLogRepository.findById(errorLogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public ErrorLogResponse.ErrorLogDetail getErrorLogDetail(Long errorLogId) {
        return ErrorLogResponse.ErrorLogDetail.from(getErrorLog(errorLogId));
    }

    /**
     * 에러 로그 확인 처리 (관리자용)
     */
    @Transactional
    public void resolveErrorLog(Long errorLogId, Long adminUserId, String memo) {
        SecurityUtils.validateSuperAdminPermission();
        String normalizedMemo = normalizeResolvedMemo(memo);
        ErrorLog errorLog = errorLogRepository.findById(errorLogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        errorLog.resolve(adminUserId, normalizedMemo);
    }

    /**
     * 에러 로그 통계 (관리자용)
     */
    @Transactional(readOnly = true)
    public ErrorLogStatsResponse getErrorLogStats() {
        SecurityUtils.validateSuperAdminPermission();
        long totalCount = errorLogRepository.count();
        long unresolvedCount = errorLogRepository.countByIsResolved("N");
        long resolvedCount = errorLogRepository.countByIsResolved("Y");

        return ErrorLogStatsResponse.builder()
                .totalCount(totalCount)
                .unresolvedCount(unresolvedCount)
                .resolvedCount(resolvedCount)
                .build();
    }

    private String normalizeResolvedMemo(String memo) {
        if (memo == null) {
            return null;
        }

        String normalizedMemo = memo.trim();
        if (normalizedMemo.length() > MAX_RESOLVED_MEMO_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedMemo.isBlank() ? null : normalizedMemo;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
