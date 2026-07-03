package com.weedrice.whiteboard.global.config;

import com.weedrice.whiteboard.domain.admin.interceptor.IpBlockInterceptor;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.ratelimit.RateLimitInterceptor;
import com.weedrice.whiteboard.global.ratelimit.RateLimitProperties;
import com.weedrice.whiteboard.global.security.JwtAuthenticationFilter;
import com.weedrice.whiteboard.global.security.AuthCookieOriginInterceptor;
import com.weedrice.whiteboard.global.security.RefererCheckInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WebConfigInterceptorTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        WebConfig.class,
        WebConfigInterceptorTest.TestController.class
})
class WebConfigInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IpBlockInterceptor ipBlockInterceptor;

    @MockitoBean
    private RefererCheckInterceptor refererCheckInterceptor;

    @MockitoBean
    private AuthCookieOriginInterceptor authCookieOriginInterceptor;

    @MockitoBean
    private RateLimitInterceptor rateLimitInterceptor;

    @MockitoBean
    private RateLimitProperties rateLimitProperties;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void setUp() throws Exception {
        when(ipBlockInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(rateLimitProperties.isEnabled()).thenReturn(false);
        when(authCookieOriginInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(refererCheckInterceptor.preHandle(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "Invalid Referer"));
    }

    @Test
    @DisplayName("emoticon APIs are excluded from referer check")
    void emoticonApis_excludedFromRefererCheck() throws Exception {
        mockMvc.perform(get("/api/v1/emoticons/purchased"))
                .andExpect(status().isOk())
                .andExpect(content().string("purchased"));

        verify(ipBlockInterceptor).preHandle(any(), any(), any());
        verifyNoInteractions(refererCheckInterceptor);
    }

    @Test
    @DisplayName("uploads path is not exposed as a static resource")
    void uploadsPath_notExposedAsStaticResource() throws Exception {
        assertThat(Arrays.stream(WebConfig.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .doesNotContain("addResourceHandlers");
    }

    @RestController
    @RequestMapping("/api/v1")
    static class TestController {

        @GetMapping("/emoticons/purchased")
        String purchasedEmoticons() {
            return "purchased";
        }
    }
}
