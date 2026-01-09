package com.weedrice.whiteboard.global.config;

import com.weedrice.whiteboard.domain.admin.interceptor.IpBlockInterceptor;
import com.weedrice.whiteboard.global.ratelimit.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final IpBlockInterceptor ipBlockInterceptor;
    private final com.weedrice.whiteboard.global.security.RefererCheckInterceptor refererCheckInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // IP 차단 인터셉터 (가장 먼저 실행)
        registry.addInterceptor(ipBlockInterceptor)
                .addPathPatterns("/**"); // 모든 경로에 대해 인터셉터 적용

        // Rate Limiting 인터셉터 (IP 차단 후, Referer 체크 전)
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**") // API 경로에 대해 Rate Limiting 적용
                .order(1); // IP 차단 후 실행

        // Referer 체크 인터셉터
        registry.addInterceptor(refererCheckInterceptor)
                .addPathPatterns("/api/**") // API 경로에 대해 Referer 체크
                .excludePathPatterns(
                        "/api/v1/auth/**",
                        "/api/v1/boards/**",
                        "/api/v1/posts/**",
                        "/api/v1/comments/**",
                        "/api/v1/files/**",
                        "/api/v1/tags/**",
                        "/api/v1/search/**",
                        "/api/v1/shop/items/**",
                        "/api/v1/ads",
                        "/api/v1/users/**",
                        "/api/v1/configs/public");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }
}
