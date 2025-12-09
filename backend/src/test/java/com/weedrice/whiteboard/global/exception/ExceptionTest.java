package com.weedrice.whiteboard.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionTest {

    @Test
    @DisplayName("BusinessException 생성 및 Getter")
    void businessException() {
        BusinessException ex = new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(ex.getMessage()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getMessage());

        BusinessException ex2 = new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Custom Message");
        assertThat(ex2.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(ex2.getMessage()).isEqualTo("Custom Message");
    }

    @Test
    @DisplayName("ErrorCode Getter")
    void errorCode() {
        ErrorCode errorCode = ErrorCode.USER_NOT_FOUND;
        assertThat(errorCode.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(errorCode.getCode()).isEqualTo("U001");
        assertThat(errorCode.getMessage()).isEqualTo("User Not Found");
    }
}
