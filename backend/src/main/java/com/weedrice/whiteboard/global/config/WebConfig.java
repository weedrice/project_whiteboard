package com.weedrice.whiteboard.global.config;

import com.weedrice.whiteboard.domain.admin.interceptor.IpBlockInterceptor;
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

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ipBlockInterceptor)
                .addPathPatterns("/**"); // 모든 경로에 대해 인터셉터 적용

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
