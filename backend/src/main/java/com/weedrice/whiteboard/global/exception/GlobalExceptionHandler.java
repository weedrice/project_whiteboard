package com.weedrice.whiteboard.global.exception;

import com.weedrice.whiteboard.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        log.warn("[{}] Business exception: {} - {}", request.getRequestURI(), e.getErrorCode().getCode(), message);
        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(ApiResponse.error(e.getErrorCode().getCode(), message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationExceptions(MethodArgumentNotValidException e, HttpServletRequest request) {
        String errorMessage = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("[{}] Validation exception: {}", request.getRequestURI(), errorMessage);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR.getCode(), errorMessage));
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
        log.error("[{}] Unexpected exception occurred", request.getRequestURI(), e); // e를 인자로 넘기면 Stack Trace가 출력됨
        String message = messageSource.getMessage(ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), null, LocaleContextHolder.getLocale());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getCode(), message));
    }
}
