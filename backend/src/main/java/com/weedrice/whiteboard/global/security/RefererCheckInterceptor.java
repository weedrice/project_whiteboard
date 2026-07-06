package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RefererCheckInterceptor implements HandlerInterceptor {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull Object handler) {
        String referer = request.getHeader("Referer");

        if (!FrontendOriginMatcher.isAllowedReferer(frontendUrl, referer)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Invalid Referer");
        }

        return true;
    }
}
