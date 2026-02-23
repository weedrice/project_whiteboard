package com.weedrice.whiteboard.global.log.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorLogTest {

    @Test
    @DisplayName("ErrorLog 생성 시 기본값 검증")
    void create_defaultValues() {
        // when
        ErrorLog errorLog = ErrorLog.builder()
                .errorCode("INTERNAL_SERVER_ERROR")
                .errorType("NullPointerException")
                .httpStatus(500)
                .message("Cannot invoke method on null")
                .requestUri("/api/v1/posts/1")
                .requestMethod("GET")
                .userId(10L)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .stackTrace("java.lang.NullPointerException...")
                .build();

        // then
        assertThat(errorLog.getErrorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(errorLog.getErrorType()).isEqualTo("NullPointerException");
        assertThat(errorLog.getHttpStatus()).isEqualTo(500);
        assertThat(errorLog.getMessage()).isEqualTo("Cannot invoke method on null");
        assertThat(errorLog.getRequestUri()).isEqualTo("/api/v1/posts/1");
        assertThat(errorLog.getRequestMethod()).isEqualTo("GET");
        assertThat(errorLog.getUserId()).isEqualTo(10L);
        assertThat(errorLog.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(errorLog.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(errorLog.getStackTrace()).isEqualTo("java.lang.NullPointerException...");
        assertThat(errorLog.getIsResolved()).isEqualTo("N");
        assertThat(errorLog.getResolvedBy()).isNull();
        assertThat(errorLog.getResolvedAt()).isNull();
        assertThat(errorLog.getResolvedMemo()).isNull();
    }

    @Test
    @DisplayName("ErrorLog 생성 - userId가 null인 경우")
    void create_withNullUserId() {
        // when
        ErrorLog errorLog = ErrorLog.builder()
                .errorCode("BAD_REQUEST")
                .errorType("BusinessException")
                .httpStatus(400)
                .message("Invalid parameter")
                .requestUri("/api/v1/users")
                .requestMethod("POST")
                .userId(null)
                .ipAddress("10.0.0.1")
                .build();

        // then
        assertThat(errorLog.getUserId()).isNull();
        assertThat(errorLog.getUserAgent()).isNull();
        assertThat(errorLog.getStackTrace()).isNull();
    }

    @Test
    @DisplayName("ErrorLog 확인 처리")
    void resolve_success() {
        // given
        ErrorLog errorLog = ErrorLog.builder()
                .errorCode("INTERNAL_SERVER_ERROR")
                .errorType("NullPointerException")
                .httpStatus(500)
                .message("Error")
                .requestUri("/api/v1/test")
                .requestMethod("GET")
                .ipAddress("127.0.0.1")
                .build();

        assertThat(errorLog.getIsResolved()).isEqualTo("N");

        // when
        errorLog.resolve(1L, "확인 완료");

        // then
        assertThat(errorLog.getIsResolved()).isEqualTo("Y");
        assertThat(errorLog.getResolvedBy()).isEqualTo(1L);
        assertThat(errorLog.getResolvedAt()).isNotNull();
        assertThat(errorLog.getResolvedMemo()).isEqualTo("확인 완료");
    }

    @Test
    @DisplayName("ErrorLog 확인 처리 - memo가 null인 경우")
    void resolve_withNullMemo() {
        // given
        ErrorLog errorLog = ErrorLog.builder()
                .errorCode("INTERNAL_SERVER_ERROR")
                .errorType("RuntimeException")
                .httpStatus(500)
                .message("Error")
                .requestUri("/api/v1/test")
                .requestMethod("GET")
                .ipAddress("127.0.0.1")
                .build();

        // when
        errorLog.resolve(2L, null);

        // then
        assertThat(errorLog.getIsResolved()).isEqualTo("Y");
        assertThat(errorLog.getResolvedBy()).isEqualTo(2L);
        assertThat(errorLog.getResolvedAt()).isNotNull();
        assertThat(errorLog.getResolvedMemo()).isNull();
    }

    @Test
    @DisplayName("ErrorLog 빌더 - 모든 필드 설정")
    void builder_allFields() {
        // when
        ErrorLog errorLog = ErrorLog.builder()
                .errorCode("FORBIDDEN")
                .errorType("AccessDeniedException")
                .httpStatus(403)
                .message("Access Denied")
                .requestUri("/api/v1/admin/users")
                .requestMethod("DELETE")
                .userId(5L)
                .ipAddress("10.10.10.10")
                .userAgent("Chrome/120.0")
                .stackTrace("org.springframework.security.access.AccessDeniedException...")
                .build();

        // then
        assertThat(errorLog.getErrorCode()).isEqualTo("FORBIDDEN");
        assertThat(errorLog.getHttpStatus()).isEqualTo(403);
        assertThat(errorLog.getRequestMethod()).isEqualTo("DELETE");
    }
}
