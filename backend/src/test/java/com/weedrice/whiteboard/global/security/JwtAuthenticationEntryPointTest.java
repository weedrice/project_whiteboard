package com.weedrice.whiteboard.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.core.AuthenticationException;

import java.io.IOException;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationEntryPointTest {

    @InjectMocks
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private AuthenticationException authException;

    @Mock
    private MessageSource messageSource;

    @Test
    @DisplayName("인증 예외 발생 시 401 Unauthorized 응답 전송")
    void commence() throws IOException {
        when(request.getLocale()).thenReturn(Locale.ENGLISH);
        when(messageSource.getMessage(eq("error.common.unauthorized"), isNull(), eq(Locale.ENGLISH)))
                .thenReturn("Unauthorized");

        jwtAuthenticationEntryPoint.commence(request, response, authException);

        verify(request).getLocale();
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }
}
