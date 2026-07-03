package com.weedrice.whiteboard.global.config;

import com.weedrice.whiteboard.domain.admin.interceptor.IpBlockInterceptor;
import com.weedrice.whiteboard.global.ratelimit.RateLimitInterceptor;
import com.weedrice.whiteboard.global.ratelimit.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final IpBlockInterceptor ipBlockInterceptor;
    private final com.weedrice.whiteboard.global.security.RefererCheckInterceptor refererCheckInterceptor;
    private final ObjectProvider<com.weedrice.whiteboard.global.security.AuthCookieOriginInterceptor> authCookieOriginInterceptorProvider;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final RateLimitProperties rateLimitProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // IP 차단 인터셉터 (가장 먼저 실행)
        registry.addInterceptor(ipBlockInterceptor)
                .addPathPatterns("/**"); // 모든 경로에 대해 인터셉터 적용

        // Rate Limiting 인터셉터 (rate-limit.enabled=true일 때만 적용)
        if (rateLimitProperties.isEnabled()) {
            registry.addInterceptor(rateLimitInterceptor)
                    .addPathPatterns("/api/**", "/files/**")
                    .order(1);
        }

        com.weedrice.whiteboard.global.security.AuthCookieOriginInterceptor authCookieOriginInterceptor =
                authCookieOriginInterceptorProvider.getIfAvailable();
        if (authCookieOriginInterceptor != null) {
            registry.addInterceptor(authCookieOriginInterceptor)
                    .addPathPatterns("/api/v1/auth/refresh", "/api/v1/auth/logout")
                    .order(2);
        }

        // Referer 체크 인터셉터
        registry.addInterceptor(refererCheckInterceptor)
                .addPathPatterns("/api/**") // API 경로에 대해 Referer 체크
                .excludePathPatterns(
                        "/api/v1/auth/**",
                        "/api/v1/agents/**",
                        "/api/v1/boards/**",
                        "/api/v1/posts/**",
                        "/api/v1/comments/**",
                        "/api/v1/emoticons/**",
                        "/api/v1/files/**",
                        "/api/v1/tags/**",
                        "/api/v1/search/**",
                        "/api/v1/shop/items/**",
                        "/api/v1/ads",
                        "/api/v1/users/**",
                        "/api/v1/configs/public");
    }

}
