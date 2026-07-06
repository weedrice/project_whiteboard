package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthCookieOriginInterceptorTest {

    private AuthCookieOriginInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AuthCookieOriginInterceptor();
        ReflectionTestUtils.setField(interceptor, "frontendUrl", "https://noviis.kr");
    }

    @Test
    void preHandle_acceptsExactOrigin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Origin", "https://noviis.kr");

        assertThatCode(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .doesNotThrowAnyException();
    }

    @Test
    void preHandle_rejectsOriginWithPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Origin", "https://noviis.kr/path");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void preHandle_rejectsPrefixMatchedAttackerOrigin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Referer", "https://noviis.kr.evil.example/auth");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void preHandle_acceptsRefererWithSameOriginPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Referer", "https://noviis.kr/auth/refresh?next=/mypage");

        assertThatCode(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .doesNotThrowAnyException();
    }
}
