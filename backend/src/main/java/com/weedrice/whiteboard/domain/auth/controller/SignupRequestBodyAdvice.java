package com.weedrice.whiteboard.domain.auth.controller;

import com.weedrice.whiteboard.domain.auth.dto.SignupRequest;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

@ControllerAdvice
@RequiredArgsConstructor
public class SignupRequestBodyAdvice extends RequestBodyAdviceAdapter {

    static final int MAX_SIGNUP_BODY_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(
            MethodParameter methodParameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return SignupRequest.class.equals(targetType);
    }

    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        long contentLength = inputMessage.getHeaders().getContentLength();
        if (contentLength > MAX_SIGNUP_BODY_BYTES) {
            throw new BusinessException(ErrorCode.REQUEST_TOO_LARGE);
        }
        byte[] body = inputMessage.getBody().readNBytes(MAX_SIGNUP_BODY_BYTES + 1);
        if (body.length > MAX_SIGNUP_BODY_BYTES) {
            throw new BusinessException(ErrorCode.REQUEST_TOO_LARGE);
        }
        JsonNode root = objectMapper.readTree(body);
        if (root != null && (root.has("provider") || root.has("providerId"))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(body);
            }

            @Override
            public org.springframework.http.HttpHeaders getHeaders() {
                return inputMessage.getHeaders();
            }
        };
    }
}
