package com.weedrice.whiteboard.global.log.aop;

import com.weedrice.whiteboard.global.log.service.LogService;
import com.weedrice.whiteboard.global.common.util.ClientUtils;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LogAspect {

    private final LogService logService;

    @Before("execution(* com.weedrice.whiteboard.domain..*Controller.*(..))")
    public void logBefore(JoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String actionType = method.getName(); // 메서드 이름을 액션 타입으로 사용

            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            String ipAddress = ClientUtils.getIp(request);

            Long userId = null;
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
                userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
            }

            // 간단한 로그 메시지 생성 (필요에 따라 파라미터 등을 JSON으로 변환하여 details에 저장 가능)
            String details = "Method: " + actionType;

            logService.saveLog(userId, actionType, ipAddress, details);
        } catch (Exception e) {
            log.error("AOP Log-saving failed", e);
        }
    }
}
