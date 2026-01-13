package com.weedrice.whiteboard.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionTest {

    @Test
    @DisplayName("BusinessException 생성 테스트")
    void createBusinessException() {
        BusinessException exception = new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(exception.getMessage()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getMessage());
    }

    @Test
    @DisplayName("BusinessException 커스텀 메시지 생성 테스트")
    void createBusinessExceptionWithCustomMessage() {
        BusinessException exception = new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Custom Message");
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(exception.getMessage()).isEqualTo("Custom Message");
    }

    @Test
    @DisplayName("ErrorCode 속성 테스트")
    void errorCodeProperties() {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        assertThat(errorCode.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode.getCode()).isEqualTo("C001");
        assertThat(errorCode.getMessage()).isEqualTo("error.common.invalidInput");
    }
}
