package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefererCheckInterceptorTest {

    private RefererCheckInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new RefererCheckInterceptor();
        ReflectionTestUtils.setField(interceptor, "frontendUrl", "https://noviis.kr");
    }

    @Test
    void preHandle_acceptsSameOriginRefererWithPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Referer", "https://noviis.kr/posts/1");

        assertThatCode(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .doesNotThrowAnyException();
    }

    @Test
    void preHandle_rejectsPrefixMatchedAttackerReferer() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Referer", "https://noviis.kr.evil.example/posts/1");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void preHandle_rejectsMissingReferer() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BusinessException.class);
    }
}
