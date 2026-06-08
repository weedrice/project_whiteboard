package com.weedrice.whiteboard.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

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

    @Test
    @DisplayName("User not found ErrorCode properties")
    void userNotFoundErrorCodeProperties() {
        ErrorCode errorCode = ErrorCode.USER_NOT_FOUND;
        assertThat(errorCode.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(errorCode.getCode()).isEqualTo("U001");
        assertThat(errorCode.getMessage()).isEqualTo("error.user.notFound");
    }

    @Test
    @DisplayName("Every ErrorCode message key exists in Korean and English resources")
    void errorCodeMessageKeysExistInMessageResources() throws Exception {
        Properties koreanMessages = loadMessages("messages.properties");
        Properties englishMessages = loadMessages("messages_en.properties");

        for (ErrorCode errorCode : ErrorCode.values()) {
            assertThat(koreanMessages)
                    .as("Korean message key for %s", errorCode.name())
                    .containsKey(errorCode.getMessage());
            assertThat(englishMessages)
                    .as("English message key for %s", errorCode.name())
                    .containsKey(errorCode.getMessage());
        }
    }

    @Test
    @DisplayName("Every ErrorCode code value is unique")
    void errorCodeValuesAreUnique() {
        assertThat(Arrays.stream(ErrorCode.values())
                .collect(Collectors.groupingBy(ErrorCode::getCode)))
                .allSatisfy((code, errorCodes) -> assertThat(errorCodes)
                        .as("ErrorCode code %s", code)
                        .hasSize(1));
    }

    private Properties loadMessages(String resourceName) throws Exception {
        Properties properties = new Properties();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            assertThat(inputStream).as("%s resource", resourceName).isNotNull();
            properties.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
