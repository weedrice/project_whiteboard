package com.weedrice.whiteboard.global.config;

import com.weedrice.whiteboard.global.config.interceptor.IpBlockInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final IpBlockInterceptor ipBlockInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ipBlockInterceptor)
                .addPathPatterns("/**"); // 모든 경로에 대해 인터셉터 적용
    }
}
