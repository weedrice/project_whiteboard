package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RefererCheckInterceptor implements HandlerInterceptor {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull Object handler)
            throws Exception {
        String referer = request.getHeader("Referer");

        // Referer가 없거나, 허용된 Frontend URL로 시작하지 않으면 차단
        // 단, 개발 환경 등 예외가 필요할 수 있으나, 여기서는 강력하게 체크
        if (referer == null || !referer.startsWith(frontendUrl)) {
            // API 요청인 경우에만 체크 (정적 리소스 등은 제외될 수 있음 -> WebConfig에서 패턴 지정)
            throw new BusinessException(ErrorCode.FORBIDDEN, "Invalid Referer");
        }

        return true;
    }
}
