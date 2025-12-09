package com.weedrice.whiteboard.global.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    @DisplayName("성공 응답 생성 (데이터 포함)")
    void success_withData() {
        String data = "Test Data";
        ApiResponse<String> response = ApiResponse.success(data);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(data);
        assertThat(response.getError()).isNull();
    }

    @Test
    @DisplayName("성공 응답 생성 (데이터 없음)")
    void success_noData() {
        ApiResponse<Void> response = ApiResponse.success();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
        assertThat(response.getError()).isNull();
    }

    @Test
    @DisplayName("에러 응답 생성 (ErrorResponse 객체)")
    void error_withObject() {
        ErrorResponse errorObj = new ErrorResponse("E001", "Error Message");
        ApiResponse<Void> response = ApiResponse.error(errorObj);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getError()).isEqualTo(errorObj);
    }

    @Test
    @DisplayName("에러 응답 생성 (코드 및 메시지)")
    void error_withCodeAndMessage() {
        String code = "E002";
        String message = "Another Error";
        ApiResponse<Void> response = ApiResponse.error(code, message);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getError()).isNotNull();
        assertThat(response.getError().getCode()).isEqualTo(code);
        assertThat(response.getError().getMessage()).isEqualTo(message);
    }
}
