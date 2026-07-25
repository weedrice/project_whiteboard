package com.weedrice.whiteboard.global.config;

import com.weedrice.whiteboard.global.ratelimit.RateLimitHeaderWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * rate limit 헤더는 CORS safelist에 없으므로 명시적으로 노출하지 않으면
 * 다른 오리진의 브라우저 JS가 읽을 수 없다. 헤더 이름 오타나 누락을 막는다.
 */
class SecurityConfigCorsTest {

    private static final String FRONTEND_URL = "https://noviis.kr";

    private CorsConfiguration corsConfiguration() {
        SecurityConfig securityConfig = new SecurityConfig(
                mock(com.weedrice.whiteboard.global.security.JwtAuthenticationFilter.class),
                mock(com.weedrice.whiteboard.global.security.JwtAuthenticationEntryPoint.class),
                mock(com.weedrice.whiteboard.global.security.oauth.CustomOAuth2UserService.class),
                mock(com.weedrice.whiteboard.global.security.oauth.OAuth2SuccessHandler.class),
                mock(org.springframework.beans.factory.ObjectProvider.class),
                mock(org.springframework.beans.factory.ObjectProvider.class));
        ReflectionTestUtils.setField(securityConfig, "frontendUrl", FRONTEND_URL);

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/posts");
        CorsConfiguration configuration = source.getCorsConfiguration(request);
        assertThat(configuration).isNotNull();
        return configuration;
    }

    @Test
    @DisplayName("rate limit 헤더 네 개를 모두 노출한다")
    void exposesRateLimitHeaders() {
        assertThat(corsConfiguration().getExposedHeaders()).containsExactlyInAnyOrder(
                RateLimitHeaderWriter.HEADER_LIMIT,
                RateLimitHeaderWriter.HEADER_REMAINING,
                RateLimitHeaderWriter.HEADER_RESET,
                RateLimitHeaderWriter.HEADER_RETRY_AFTER);
    }

    @Test
    @DisplayName("자격 증명 허용과 단일 오리진 제한을 유지한다")
    void keepsCredentialedSingleOriginPolicy() {
        CorsConfiguration configuration = corsConfiguration();

        assertThat(configuration.getAllowCredentials()).isTrue();
        assertThat(configuration.getAllowedOrigins()).containsExactly(FRONTEND_URL);
    }
}
