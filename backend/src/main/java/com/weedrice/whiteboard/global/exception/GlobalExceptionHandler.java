package com.weedrice.whiteboard.global.exception;

import com.weedrice.whiteboard.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        String message = messageSource.getMessage(e.getErrorCode().getMessage(), null, LocaleContextHolder.getLocale());
        MDC.put("errorCode", e.getErrorCode().getCode());
        MDC.put("errorType", "BusinessException");
        log.warn("[{}] Business exception: {} - {}", request.getRequestURI(), e.getErrorCode().getCode(), message);
        MDC.remove("errorCode");
        MDC.remove("errorType");
        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(ApiResponse.error(e.getErrorCode().getCode(), message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationExceptions(MethodArgumentNotValidException e, HttpServletRequest request) {
        // 모든 필드 에러를 수집
        java.util.Map<String, java.util.List<String>> errors = new java.util.HashMap<>();
        
        e.getBindingResult().getFieldErrors().forEach(error -> {
            String field = error.getField();
            String message = error.getDefaultMessage();
            errors.computeIfAbsent(field, k -> new java.util.ArrayList<>()).add(message);
        });
        
        // 필드 에러가 없는 경우 (글로벌 에러)
        if (errors.isEmpty()) {
            e.getBindingResult().getGlobalErrors().forEach(error -> {
                String objectName = error.getObjectName();
                String message = error.getDefaultMessage();
                errors.computeIfAbsent(objectName, k -> new java.util.ArrayList<>()).add(message);
            });
        }
        
        String summaryMessage = errors.isEmpty() 
            ? "Validation failed" 
            : String.format("Validation failed for %d field(s)", errors.size());
        
        log.warn("[{}] Validation exception: {} - {}", request.getRequestURI(), summaryMessage, errors);
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR.getCode(), summaryMessage, errors));
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleLoginException(Exception e, HttpServletRequest request) {
        String message = messageSource.getMessage(ErrorCode.LOGIN_FAILED.getMessage(), null, LocaleContextHolder.getLocale());
        log.warn("[{}] Login failed: {}", request.getRequestURI(), e.getMessage());
        return ResponseEntity
                .status(ErrorCode.LOGIN_FAILED.getStatus())
                .body(ApiResponse.error(ErrorCode.LOGIN_FAILED.getCode(), message));
    }

    @ExceptionHandler({LockedException.class, DisabledException.class})
    public ResponseEntity<ApiResponse<Void>> handleAccountStatusException(Exception e, HttpServletRequest request) {
        String message = messageSource.getMessage(ErrorCode.USER_NOT_ACTIVE.getMessage(), null, LocaleContextHolder.getLocale());
        log.warn("[{}] Account status exception: {}", request.getRequestURI(), e.getMessage());
        return ResponseEntity
                .status(ErrorCode.USER_NOT_ACTIVE.getStatus())
                .body(ApiResponse.error(ErrorCode.USER_NOT_ACTIVE.getCode(), message));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request) {
        String message = messageSource.getMessage(ErrorCode.FORBIDDEN.getMessage(), null, LocaleContextHolder.getLocale());
        log.warn("[{}] Access denied: {}", request.getRequestURI(), e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.FORBIDDEN.getCode(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllUncaughtException(Exception e, HttpServletRequest request) {
        MDC.put("errorType", e.getClass().getSimpleName());
        MDC.put("errorMessage", e.getMessage());
        log.error("[{}] Unexpected exception occurred: {} - {}", 
                request.getRequestURI(), e.getClass().getSimpleName(), e.getMessage(), e);
        MDC.remove("errorType");
        MDC.remove("errorMessage");
        
        String message = messageSource.getMessage(ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), null, LocaleContextHolder.getLocale());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getCode(), message));
    }
}
