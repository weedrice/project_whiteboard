package com.weedrice.whiteboard.global.config;

import com.weedrice.whiteboard.global.security.CurrentAgentIdArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class CurrentAgentIdWebMvcConfig implements WebMvcConfigurer {

    private final CurrentAgentIdArgumentResolver currentAgentIdArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentAgentIdArgumentResolver);
    }
}
