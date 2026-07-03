package com.weedrice.whiteboard.global.exception;

import com.weedrice.whiteboard.domain.agent.dto.AgentLimits;
import com.weedrice.whiteboard.domain.agent.dto.AgentRestrictions;
import com.weedrice.whiteboard.domain.agent.dto.AgentWriteErrorDetails;
import com.weedrice.whiteboard.domain.agent.exception.AgentWriteErrorCode;
import com.weedrice.whiteboard.domain.agent.exception.AgentWriteException;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.util.ClientIpResolver;
import com.weedrice.whiteboard.global.log.service.ErrorLogService;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    @Mock
    private ErrorLogService errorLogService;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setRequestURI("/test/uri");
        ReflectionTestUtils.setField(globalExceptionHandler, "errorLogService", errorLogService);
    }

    @Test
    void handleAgentWriteException_includesStructuredDetailsAndSuppressesExpectedNoise() {
        AgentLimits limits = AgentLimits.builder()
                .maxPostsPerDay(50)
                .maxCommentsPerDay(100)
                .postsRemaining(0)
                .commentsRemaining(92)
                .build();
        AgentRestrictions restrictions = AgentRestrictions.builder()
                .canPost(false)
                .canComment(true)
                .suspended(false)
                .build();
        AgentWriteException ex = new AgentWriteException(
                AgentWriteErrorCode.POST_DAILY_LIMIT_EXCEEDED,
                null,
                "create_post",
                limits,
                restrictions,
                null,
                null);

        ResponseEntity<ApiResponse<Object>> response = globalExceptionHandler.handleAgentWriteException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getError().getCode()).isEqualTo("post_daily_limit_exceeded");
        AgentWriteErrorDetails details = (AgentWriteErrorDetails) response.getBody().getError().getDetails();
        assertThat(details.getAction()).isEqualTo("create_post");
        assertThat(details.getLimits()).isSameAs(limits);
        assertThat(details.getRestrictions()).isSameAs(restrictions);
        verify(errorLogService, never()).saveErrorLog(anyString(), anyString(), anyInt(), anyString(),
                anyString(), anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    void handleAgentWriteException_logsNonSuppressedErrors() {
        AgentWriteException ex = new AgentWriteException(
                AgentWriteErrorCode.CONTENT_ENCODING_INVALID,
                "Content appears corrupted.",
                "create_post",
                AgentLimits.builder().build(),
                AgentRestrictions.builder().build(),
                null,
                null);

        ResponseEntity<ApiResponse<Object>> response = globalExceptionHandler.handleAgentWriteException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("content_encoding_invalid");
        verify(errorLogService).saveErrorLog(
                eq("content_encoding_invalid"),
                eq("AgentWriteException"),
                eq(HttpStatus.BAD_REQUEST.value()),
                eq("Content appears corrupted."),
                anyString(),
                anyString(),
                isNull(),
                anyString(),
                isNull(),
                isNull());
    }

    @Test
    void handleAgentWriteException_mapsNotFoundErrorStatus() {
        AgentWriteException ex = new AgentWriteException(
                AgentWriteErrorCode.BOARD_NOT_FOUND,
                null,
                "create_post",
                AgentLimits.builder().build(),
                AgentRestrictions.builder().build(),
                null,
                null);

        ResponseEntity<ApiResponse<Object>> response = globalExceptionHandler.handleAgentWriteException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("board_not_found");
        assertThat(response.getBody().getError().getMessage()).isEqualTo("Board not found.");
    }

    @Test
    void handleAgentWriteException_mapsForbiddenErrorStatus() {
        AgentWriteException ex = new AgentWriteException(
                AgentWriteErrorCode.CATEGORY_WRITE_FORBIDDEN,
                null,
                "create_post",
                AgentLimits.builder().build(),
                AgentRestrictions.builder().build(),
                null,
                null);

        ResponseEntity<ApiResponse<Object>> response = globalExceptionHandler.handleAgentWriteException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("category_write_forbidden");
        assertThat(response.getBody().getError().getMessage()).isEqualTo("Agent cannot write to this category.");
    }

    @Test
    void handleBusinessException_logsResolvedClientIpWhenAvailable() {
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        ReflectionTestUtils.setField(globalExceptionHandler, "clientIpResolver", clientIpResolver);
        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.11");
        when(messageSource.getMessage(eq(ErrorCode.USER_NOT_FOUND.getMessage()), isNull(), any(Locale.class)))
                .thenReturn(ErrorCode.USER_NOT_FOUND.getMessage());

        globalExceptionHandler.handleBusinessException(new BusinessException(ErrorCode.USER_NOT_FOUND), request);

        verify(errorLogService).saveErrorLog(
                eq(ErrorCode.USER_NOT_FOUND.getCode()),
                eq("BusinessException"),
                eq(HttpStatus.NOT_FOUND.value()),
                anyString(),
                anyString(),
                anyString(),
                isNull(),
                eq("203.0.113.11"),
                isNull(),
                isNull());
    }

    @Test
    @DisplayName("BusinessException 처리")
    void handleBusinessException() {
        // given
        BusinessException ex = new BusinessException(ErrorCode.USER_NOT_FOUND);
        when(messageSource.getMessage(eq(ErrorCode.USER_NOT_FOUND.getMessage()), isNull(), any(Locale.class)))
                .thenReturn(ErrorCode.USER_NOT_FOUND.getMessage());

        // when
        ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleBusinessException(ex, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getError().getCode()).isEqualTo(ErrorCode.USER_NOT_FOUND.getCode());
        verify(errorLogService).saveErrorLog(
                eq(ErrorCode.USER_NOT_FOUND.getCode()),
                eq("BusinessException"),
                eq(HttpStatus.NOT_FOUND.value()),
                anyString(),
                anyString(),
                anyString(),
                isNull(),
                anyString(),
                isNull(),
                isNull());
    }

    @Test
    @DisplayName("BOARD_NOT_FOUND 는 에러 로그를 저장하지 않음")
    void handleBusinessException_boardNotFound_doesNotSaveErrorLog() {
        BusinessException ex = new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        when(messageSource.getMessage(eq(ErrorCode.BOARD_NOT_FOUND.getMessage()), isNull(), any(Locale.class)))
                .thenReturn(ErrorCode.BOARD_NOT_FOUND.getMessage());

        ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleBusinessException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo(ErrorCode.BOARD_NOT_FOUND.getCode());
        verify(errorLogService, never()).saveErrorLog(anyString(), anyString(), anyInt(), anyString(),
                anyString(), anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("RATE_LIMIT_EXCEEDED 는 에러 로그를 저장하지 않음")
    void handleBusinessException_rateLimitExceeded_doesNotSaveErrorLog() {
        BusinessException ex = new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
        when(messageSource.getMessage(eq(ErrorCode.RATE_LIMIT_EXCEEDED.getMessage()), isNull(), any(Locale.class)))
                .thenReturn(ErrorCode.RATE_LIMIT_EXCEEDED.getMessage());

        ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleBusinessException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED.getCode());
        verify(errorLogService, never()).saveErrorLog(anyString(), anyString(), anyInt(), anyString(),
                anyString(), anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("refresh token 예외는 세션 만료 메시지로 응답하고 에러 로그 저장을 생략한다")
    void handleBusinessException_refreshTokenError_masksResponseMessage() {
        BusinessException ex = new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        request.setRequestURI("/api/v1/auth/refresh");
        when(messageSource.getMessage(eq(ErrorCode.INVALID_REFRESH_TOKEN.getMessage()), isNull(), any(Locale.class)))
                .thenReturn("유효하지 않은 리프레시 토큰입니다.");
        when(messageSource.getMessage(eq("error.auth.sessionExpired"), isNull(), any(Locale.class)))
                .thenReturn("세션이 만료되었습니다. 다시 로그인해주세요.");

        ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleBusinessException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN.getCode());
        assertThat(response.getBody().getError().getMessage()).isEqualTo("세션이 만료되었습니다. 다시 로그인해주세요.");
        verify(errorLogService, never()).saveErrorLog(anyString(), anyString(), anyInt(), anyString(),
                anyString(), anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("refresh 외 경로의 refresh token 예외는 기존 메시지를 유지한다")
    void handleBusinessException_nonRefreshUriKeepsOriginalMessage() {
        BusinessException ex = new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        when(messageSource.getMessage(eq(ErrorCode.INVALID_REFRESH_TOKEN.getMessage()), isNull(), any(Locale.class)))
                .thenReturn("유효하지 않은 리프레시 토큰입니다.");

        ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleBusinessException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN.getCode());
        assertThat(response.getBody().getError().getMessage()).isEqualTo("유효하지 않은 리프레시 토큰입니다.");
        verify(errorLogService).saveErrorLog(
                eq(ErrorCode.INVALID_REFRESH_TOKEN.getCode()),
                eq("BusinessException"),
                eq(HttpStatus.UNAUTHORIZED.value()),
                eq("유효하지 않은 리프레시 토큰입니다."),
                anyString(),
                anyString(),
                isNull(),
                anyString(),
                isNull(),
                isNull());
    }

    @Test
    @DisplayName("Validation Exception 처리")
    void handleValidationExceptions() {
        // given
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError titleError = new FieldError("objectName", "title", null, false, null, null, "must not be null");
        FieldError contentError = new FieldError("objectName", "content", null, false, null, null, "must not be blank");
        FieldError titleSizeError = new FieldError("objectName", "title", null, false, null, null, "size must be valid");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(titleError, contentError, titleSizeError));
        when(messageSource.getMessage(eq("error.common.validationFailedSummaryFields"), any(), any(Locale.class)))
                .thenReturn("Validation failed");
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);

        // when
        ResponseEntity<ApiResponse<Object>> response = globalExceptionHandler.handleValidationExceptions(ex, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getError().getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
        assertThat(response.getBody().getError().getMessage()).contains("Validation failed");
        @SuppressWarnings("unchecked")
        Map<String, List<String>> details = (Map<String, List<String>>) response.getBody().getError().getDetails();
        assertThat(details.keySet()).containsExactly("title", "content");
        assertThat(details.get("title")).containsExactly("must not be null", "size must be valid");
    }

    @Test
    @DisplayName("HttpMessageNotReadableException 처리")
    void handleHttpMessageNotReadableException() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Malformed request");
        when(messageSource.getMessage(eq("error.common.validationFailedSummary"), isNull(), any(Locale.class)))
                .thenReturn("Validation failed.");

        ResponseEntity<ApiResponse<Void>> response =
                globalExceptionHandler.handleHttpMessageNotReadableException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getError().getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
        assertThat(response.getBody().getError().getMessage()).isEqualTo("Validation failed.");
    }

    @Test
    @DisplayName("쿼리 파라미터 타입 변환 예외는 400으로 처리")
    void handleMethodArgumentTypeMismatchException() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Integer.class, "page", mock(MethodParameter.class), new NumberFormatException("abc"));
        when(messageSource.getMessage(eq("error.common.validationFailedSummary"), isNull(), any(Locale.class)))
                .thenReturn("Validation failed.");

        ResponseEntity<ApiResponse<Void>> response =
                globalExceptionHandler.handleMethodArgumentTypeMismatchException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getError().getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
    }

    @Test
    @DisplayName("필수 요청 파라미터 누락 예외는 400으로 처리")
    void handleMissingServletRequestParameterException() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("q", "String");
        when(messageSource.getMessage(eq("error.common.validationFailedSummary"), isNull(), any(Locale.class)))
                .thenReturn("Validation failed.");

        ResponseEntity<ApiResponse<Void>> response =
                globalExceptionHandler.handleMissingServletRequestParameterException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getError().getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
        assertThat(response.getBody().getError().getMessage()).isEqualTo("Validation failed.");
        verify(errorLogService).saveErrorLog(
                eq(ErrorCode.VALIDATION_ERROR.getCode()),
                eq("MissingServletRequestParameterException"),
                eq(HttpStatus.BAD_REQUEST.value()),
                eq("Validation failed."),
                anyString(),
                anyString(),
                isNull(),
                anyString(),
                isNull(),
                isNull());
    }

    @Test
    void handleConstraintViolationException() {
        ConstraintViolationException ex = new ConstraintViolationException("invalid parameter", Collections.emptySet());
        when(messageSource.getMessage(eq("error.common.validationFailedSummary"), isNull(), any(Locale.class)))
                .thenReturn("Validation failed.");

        ResponseEntity<ApiResponse<Void>> response =
                globalExceptionHandler.handleConstraintViolationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getError().getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
        assertThat(response.getBody().getError().getMessage()).isEqualTo("Validation failed.");
        verify(errorLogService).saveErrorLog(
                eq(ErrorCode.VALIDATION_ERROR.getCode()),
                eq("ConstraintViolationException"),
                eq(HttpStatus.BAD_REQUEST.value()),
                eq("Validation failed."),
                anyString(),
                anyString(),
                isNull(),
                anyString(),
                isNull(),
                isNull());
    }

    @Test
    @DisplayName("AccessDeniedException 처리")
    void handleAccessDeniedException() {
        // given
        AccessDeniedException ex = new AccessDeniedException("Access Denied");

        // when
        ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleAccessDeniedException(ex, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getError().getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("기타 Exception 처리")
    void handleAllUncaughtException() {
        // given
        Exception ex = new Exception("Unexpected error");
        when(messageSource.getMessage(eq(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()), isNull(), any(Locale.class)))
                .thenReturn(ErrorCode.INTERNAL_SERVER_ERROR.getMessage());

        // when
        ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleAllUncaughtException(ex, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getError().getCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getCode());
    }
}
