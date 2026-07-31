package com.weedrice.whiteboard.global.exception;

import com.weedrice.whiteboard.domain.agent.exception.AgentWriteErrorCode;
import com.weedrice.whiteboard.domain.agent.exception.AgentWriteException;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.ratelimit.RateLimitExceededException;
import com.weedrice.whiteboard.global.ratelimit.RateLimitHeaderWriter;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    private static final String AUTH_REFRESH_URI = "/api/v1/auth/refresh";
    private static final String AUTH_SESSION_EXPIRED_MESSAGE = "error.auth.sessionExpired";
    private static final Set<ErrorCode> SUPPRESSED_REFRESH_BUSINESS_CODES = EnumSet.of(
            ErrorCode.INVALID_REFRESH_TOKEN,
            ErrorCode.EXPIRED_REFRESH_TOKEN
    );
    private static final Set<ErrorCode> SUPPRESSED_ERROR_LOG_BUSINESS_CODES = EnumSet.of(
            ErrorCode.BOARD_NOT_FOUND,
            ErrorCode.RATE_LIMIT_EXCEEDED
    );
    private static final Set<String> SUPPRESSED_AGENT_WRITE_ERROR_CODES = Set.of(
            AgentWriteErrorCode.BOARD_NOT_FOUND,
            AgentWriteErrorCode.POST_DAILY_LIMIT_EXCEEDED,
            AgentWriteErrorCode.COMMENT_DAILY_LIMIT_EXCEEDED,
            AgentWriteErrorCode.NOTE_DAILY_LIMIT_EXCEEDED
    ).stream()
            .map(AgentWriteErrorCode::getCode)
            .collect(Collectors.toUnmodifiableSet());

    private final MessageSource messageSource;
    private final ValidationErrorCollector validationErrorCollector;
    private final ExceptionErrorLogReporter errorLogReporter;

    @ExceptionHandler(AgentWriteException.class)
    public ResponseEntity<ApiResponse<Object>> handleAgentWriteException(AgentWriteException e,
            HttpServletRequest request) {
        HttpStatus status = AgentWriteErrorMapper.statusOf(e);
        MDC.put("errorCode", e.getCode());
        MDC.put("errorType", "AgentWriteException");
        log.warn("[{}] Agent write exception: {} - {}", request.getRequestURI(), e.getCode(), e.getMessage());
        MDC.remove("errorCode");
        MDC.remove("errorType");

        if (!SUPPRESSED_AGENT_WRITE_ERROR_CODES.contains(e.getCode())) {
            errorLogReporter.report(e.getCode(), "AgentWriteException",
                    status.value(), e.getMessage(), request, null);
        }

        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(e.getCode(), e.getMessage(), AgentWriteErrorMapper.detailsOf(e)));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        String message = resolveBusinessExceptionMessage(e);
        String responseMessage = resolveResponseMessage(request, e.getErrorCode(), message);
        MDC.put("errorCode", e.getErrorCode().getCode());
        MDC.put("errorType", "BusinessException");
        log.warn("[{}] Business exception: {} - {}", request.getRequestURI(), e.getErrorCode().getCode(), message);
        MDC.remove("errorCode");
        MDC.remove("errorType");

        if (!shouldSuppressBusinessErrorLog(request, e.getErrorCode())) {
            errorLogReporter.report(e.getErrorCode().getCode(), "BusinessException",
                    e.getErrorCode().getStatus().value(), message, request, null);
        }

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(e.getErrorCode().getStatus());
        if (e instanceof RateLimitExceededException rateLimitExceededException) {
            RateLimitHeaderWriter.apply(responseBuilder, rateLimitExceededException.getSnapshot());
        }

        return responseBuilder.body(ApiResponse.error(e.getErrorCode().getCode(), responseMessage));
    }

    private String resolveBusinessExceptionMessage(BusinessException e) {
        if (e.getMessageKey() != null) {
            return messageSource.getMessage(e.getMessageKey(), e.getMessageArguments(),
                    LocaleContextHolder.getLocale());
        }

        // Legacy custom messages remain supported while callers migrate to message keys.
        return (e.getMessage() != null && !e.getMessage().equals(e.getErrorCode().getMessage()))
                ? e.getMessage()
                : messageSource.getMessage(e.getErrorCode().getMessage(), null, LocaleContextHolder.getLocale());
    }

    private String resolveResponseMessage(HttpServletRequest request, ErrorCode errorCode, String defaultMessage) {
        if (isRefreshTokenBusinessError(request, errorCode)) {
            return messageSource.getMessage(AUTH_SESSION_EXPIRED_MESSAGE, null, LocaleContextHolder.getLocale());
        }
        return defaultMessage;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationExceptions(MethodArgumentNotValidException e,
            HttpServletRequest request) {
        // 모든 필드 에러를 수집
        Map<String, List<String>> errors = validationErrorCollector.collect(e);

        String summaryMessage = errors.isEmpty()
                ? messageSource.getMessage("error.common.validationFailedSummary", null,
                        LocaleContextHolder.getLocale())
                : messageSource.getMessage("error.common.validationFailedSummaryFields", new Object[] { errors.size() },
                        LocaleContextHolder.getLocale());

        log.warn("[{}] Validation exception: {} - {}", request.getRequestURI(), summaryMessage, errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR.getCode(), summaryMessage, errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e,
            HttpServletRequest request) {
        log.warn("[{}] Request body parse exception: {}", request.getRequestURI(), e.getMessage());

        // 어느 필드가 문제인지 특정할 수 없는 유일한 검증 경로이므로 details 없이 응답한다.
        return validationErrorResponse(null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e,
            HttpServletRequest request) {
        log.warn("[{}] Request parameter type mismatch: {} - {}", request.getRequestURI(), e.getName(),
                e.getValue());

        return validationErrorResponse(
                validationErrorCollector.singleFieldError(e.getName(), "error.common.typeMismatch"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e,
            HttpServletRequest request) {
        log.warn("[{}] Missing request parameter: {} ({})", request.getRequestURI(), e.getParameterName(),
                e.getParameterType());

        return validationErrorResponse(validationErrorCollector.singleFieldError(
                e.getParameterName(),
                "error.common.missingParameter"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolationException(
            ConstraintViolationException e,
            HttpServletRequest request) {
        log.warn("[{}] Constraint violation: {}", request.getRequestURI(), e.getMessage());

        return validationErrorResponse(validationErrorCollector.collect(e));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleHandlerMethodValidationException(
            HandlerMethodValidationException e,
            HttpServletRequest request) {
        log.warn("[{}] Handler method validation failed: {}", request.getRequestURI(), e.getReason());

        return validationErrorResponse(validationErrorCollector.collect(e));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e,
            HttpServletRequest request) {
        String message = messageSource.getMessage(ErrorCode.METHOD_NOT_ALLOWED.getMessage(), null,
                LocaleContextHolder.getLocale());
        log.warn("[{}] Method not allowed: {}", request.getRequestURI(), e.getMethod());
        errorLogReporter.report(ErrorCode.METHOD_NOT_ALLOWED.getCode(), "HttpRequestMethodNotSupportedException",
                HttpStatus.METHOD_NOT_ALLOWED.value(), message, request, null);
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(ErrorCode.METHOD_NOT_ALLOWED.getCode(), message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(
            DataIntegrityViolationException e,
            HttpServletRequest request) {
        String message = messageSource.getMessage(ErrorCode.DUPLICATE_RESOURCE.getMessage(), null,
                LocaleContextHolder.getLocale());
        log.warn("[{}] Data integrity violation: {}", request.getRequestURI(), e.getClass().getSimpleName());
        errorLogReporter.report(ErrorCode.DUPLICATE_RESOURCE.getCode(), "DataIntegrityViolationException",
                HttpStatus.CONFLICT.value(), message, request, null);
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCode.DUPLICATE_RESOURCE.getCode(), message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException e,
            HttpServletRequest request) {
        String message = messageSource.getMessage(ErrorCode.FILE_TOO_LARGE.getMessage(), null,
                LocaleContextHolder.getLocale());
        log.warn("[{}] Upload size exceeded", request.getRequestURI());
        errorLogReporter.report(ErrorCode.FILE_TOO_LARGE.getCode(), "MaxUploadSizeExceededException",
                HttpStatus.BAD_REQUEST.value(), message, request, null);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.FILE_TOO_LARGE.getCode(), message));
    }

    /**
     * 검증 실패 응답을 만든다. {@code errors}가 비어 있지 않으면 {@code MethodArgumentNotValidException}
     * 경로와 같은 {@code {필드명: [메시지]}} 형태로 details를 채워, 클라이언트가 검증 경로에 관계없이
     * 동일한 방식으로 필드 단위 오류를 표시할 수 있게 한다.
     */
    private ResponseEntity<ApiResponse<Object>> validationErrorResponse(Map<String, List<String>> errors) {
        boolean hasFieldErrors = errors != null && !errors.isEmpty();
        String message = hasFieldErrors
                ? messageSource.getMessage("error.common.validationFailedSummaryFields",
                        new Object[] { errors.size() }, LocaleContextHolder.getLocale())
                : messageSource.getMessage("error.common.validationFailedSummary", null,
                        LocaleContextHolder.getLocale());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR.getCode(), message,
                        hasFieldErrors ? errors : null));
    }

    @ExceptionHandler({ BadCredentialsException.class, UsernameNotFoundException.class })
    public ResponseEntity<ApiResponse<Void>> handleLoginException(Exception e, HttpServletRequest request) {
        String message = messageSource.getMessage(ErrorCode.LOGIN_FAILED.getMessage(), null,
                LocaleContextHolder.getLocale());
        log.warn("[{}] Login failed: {}", request.getRequestURI(), e.getMessage());

        errorLogReporter.report(ErrorCode.LOGIN_FAILED.getCode(), e.getClass().getSimpleName(),
                ErrorCode.LOGIN_FAILED.getStatus().value(), message, request, null);

        return ResponseEntity
                .status(ErrorCode.LOGIN_FAILED.getStatus())
                .body(ApiResponse.error(ErrorCode.LOGIN_FAILED.getCode(), message));
    }

    @ExceptionHandler({ LockedException.class, DisabledException.class })
    public ResponseEntity<ApiResponse<Void>> handleAccountStatusException(Exception e, HttpServletRequest request) {
        String message = messageSource.getMessage(ErrorCode.USER_NOT_ACTIVE.getMessage(), null,
                LocaleContextHolder.getLocale());
        log.warn("[{}] Account status exception: {}", request.getRequestURI(), e.getMessage());

        errorLogReporter.report(ErrorCode.USER_NOT_ACTIVE.getCode(), e.getClass().getSimpleName(),
                ErrorCode.USER_NOT_ACTIVE.getStatus().value(), message, request, null);

        return ResponseEntity
                .status(ErrorCode.USER_NOT_ACTIVE.getStatus())
                .body(ApiResponse.error(ErrorCode.USER_NOT_ACTIVE.getCode(), message));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException e,
            HttpServletRequest request) {
        String message = messageSource.getMessage(ErrorCode.FORBIDDEN.getMessage(), null,
                LocaleContextHolder.getLocale());
        log.warn("[{}] Access denied: {}", request.getRequestURI(), e.getMessage());

        errorLogReporter.report(ErrorCode.FORBIDDEN.getCode(), "AccessDeniedException",
                HttpStatus.FORBIDDEN.value(), message, request, null);

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.FORBIDDEN.getCode(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllUncaughtException(Exception e, HttpServletRequest request) {
        String exceptionMessage = resolveExceptionMessage(e);
        MDC.put("errorType", e.getClass().getSimpleName());
        MDC.put("errorMessage", exceptionMessage);
        log.error("[{}] Unexpected exception occurred: {} - {}",
                request.getRequestURI(), e.getClass().getSimpleName(), exceptionMessage, e);
        MDC.remove("errorType");
        MDC.remove("errorMessage");

        // 5xx 에러는 스택 트레이스도 저장
        String stackTrace = errorLogReporter.stackTrace(e);
        errorLogReporter.report(ErrorCode.INTERNAL_SERVER_ERROR.getCode(), e.getClass().getSimpleName(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(), exceptionMessage, request, stackTrace);

        String message = messageSource.getMessage(ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), null,
                LocaleContextHolder.getLocale());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getCode(), message));
    }

    private String resolveExceptionMessage(Exception e) {
        if (e.getMessage() != null && !e.getMessage().isBlank()) {
            return e.getMessage();
        }
        return e.getClass().getName();
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException e,
            HttpServletRequest request) {
        String message = messageSource.getMessage(ErrorCode.CONCURRENT_MODIFICATION.getMessage(), null,
                LocaleContextHolder.getLocale());
        log.warn("[{}] Concurrent entity update: {}", request.getRequestURI(), e.getPersistentClassName());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCode.CONCURRENT_MODIFICATION.getCode(), message));
    }

    private boolean shouldSuppressBusinessErrorLog(HttpServletRequest request, ErrorCode errorCode) {
        if (errorCode == null) {
            return false;
        }

        if (SUPPRESSED_ERROR_LOG_BUSINESS_CODES.contains(errorCode)) {
            return true;
        }

        if (request == null) {
            return false;
        }

        return isRefreshTokenBusinessError(request, errorCode);
    }

    private boolean isRefreshTokenBusinessError(HttpServletRequest request, ErrorCode errorCode) {
        if (request == null || errorCode == null) {
            return false;
        }

        String requestUri = normalizeRequestUri(request.getRequestURI());
        return AUTH_REFRESH_URI.equals(requestUri) && SUPPRESSED_REFRESH_BUSINESS_CODES.contains(errorCode);
    }

    private String normalizeRequestUri(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return "";
        }

        String normalized = requestUri;
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
