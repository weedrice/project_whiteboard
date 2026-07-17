package com.weedrice.whiteboard.domain.auth.controller;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignupRequestBodyAdviceTest {

    private final SignupRequestBodyAdvice advice = new SignupRequestBodyAdvice(new ObjectMapper());

    @Test
    void acceptsBodyAtExactByteLimit() throws Exception {
        byte[] prefix = "{\"loginId\":\"user\"}".getBytes(StandardCharsets.UTF_8);
        byte[] body = (new String(prefix, StandardCharsets.UTF_8)
                + " ".repeat(SignupRequestBodyAdvice.MAX_SIGNUP_BODY_BYTES - prefix.length))
                .getBytes(StandardCharsets.UTF_8);

        HttpInputMessage replayable = advice.beforeBodyRead(message(body, body.length), null, null, null);

        assertThat(replayable.getBody().readAllBytes()).isEqualTo(body);
    }

    @Test
    void rejectsBodyBeyondLimitWhenContentLengthIsUnknown() {
        byte[] body = " ".repeat(SignupRequestBodyAdvice.MAX_SIGNUP_BODY_BYTES + 1)
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> advice.beforeBodyRead(message(body, -1), null, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REQUEST_TOO_LARGE));
    }

    @Test
    void rejectsDeclaredOversizedBodyBeforeParsing() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> advice.beforeBodyRead(
                message(body, SignupRequestBodyAdvice.MAX_SIGNUP_BODY_BYTES + 1L), null, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REQUEST_TOO_LARGE));
    }

    @Test
    void stillRejectsLegacyProviderFields() {
        byte[] body = "{\"provider\":null}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> advice.beforeBodyRead(message(body, body.length), null, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    private HttpInputMessage message(byte[] body, long contentLength) {
        HttpHeaders headers = new HttpHeaders();
        if (contentLength >= 0) {
            headers.setContentLength(contentLength);
        }
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(body);
            }

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
    }
}
