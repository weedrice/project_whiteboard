package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthCookieOriginInterceptor implements HandlerInterceptor {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull Object handler) {
        String origin = request.getHeader("Origin");
        if (StringUtils.hasText(origin)) {
            if (FrontendOriginMatcher.isAllowedOriginHeader(frontendUrl, origin)) {
                return true;
            }
            throw BusinessException.withMessageKey(ErrorCode.FORBIDDEN, "error.security.invalidOrigin");
        }

        String referer = request.getHeader("Referer");
        if (StringUtils.hasText(referer) && FrontendOriginMatcher.isAllowedReferer(frontendUrl, referer)) {
            return true;
        }

        throw BusinessException.withMessageKey(ErrorCode.FORBIDDEN, "error.security.missingOrigin");
    }
}
