package com.weedrice.whiteboard.global.log.service;

import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.log.dto.ErrorLogSearchRequest;
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

    private final ErrorLogRepository errorLogRepository;

    /**
     * 에러 로그 비동기 저장
     * 별도 트랜잭션으로 실행하여 원래 요청의 트랜잭션에 영향을 주지 않음
     */
    @Async("taskExecutor")
    @Transactional(propagation = REQUIRES_NEW)
    public void saveErrorLog(ErrorLog errorLog) {
        errorLogRepository.save(errorLog);
    }

    /**
     * 에러 로그 비동기 저장 (빌더 파라미터)
     */
    @Async("taskExecutor")
    @Transactional(propagation = REQUIRES_NEW)
    public void saveErrorLog(String errorCode, String errorType, int httpStatus, String message,
            String requestUri, String requestMethod, Long userId,
            String ipAddress, String userAgent, String stackTrace) {
        ErrorLog errorLog = ErrorLog.builder()
                .errorCode(errorCode)
                .errorType(errorType)
                .httpStatus(httpStatus)
                .message(message != null && message.length() > 500 ? message.substring(0, 500) : message)
                .requestUri(requestUri != null && requestUri.length() > 500 ? requestUri.substring(0, 500) : requestUri)
                .requestMethod(requestMethod)
                .userId(userId)
                .ipAddress(ipAddress)
                .userAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent)
                .stackTrace(stackTrace)
                .build();
        errorLogRepository.save(errorLog);
    }

    /**
     * 에러 로그 검색 조회 (관리자용)
     */
    @Transactional(readOnly = true)
    public Page<ErrorLog> getErrorLogs(ErrorLogSearchRequest condition, Pageable pageable) {
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

    /**
     * 에러 로그 확인 처리 (관리자용)
     */
    @Transactional
    public void resolveErrorLog(Long errorLogId, Long adminUserId, String memo) {
        SecurityUtils.validateSuperAdminPermission();
        ErrorLog errorLog = errorLogRepository.findById(errorLogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        errorLog.resolve(adminUserId, memo);
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
}
