package com.weedrice.whiteboard.global.log.aop;

import com.weedrice.whiteboard.global.log.filter.SensitiveDataMaskingFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.UUID;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    // Pointcut: com.weedrice.whiteboard.domain 패키지 하위의 모든 controller 패키지의 모든 메서드
    @Pointcut("execution(* com.weedrice.whiteboard.domain..controller..*.*(..))")
    public void controllerMethods() {
    }

    @Around("controllerMethods()")
    public Object logControllerExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString().substring(0, 8); // 요청 추적용 ID (짧게)
        
        // MDC에 요청 ID 설정 (모든 로그에 자동 포함)
        MDC.put("requestId", requestId);
        
        // Request 정보 가져오기 (try 블록 밖에서 선언)
        HttpServletRequest request = null;
        String method = "UNKNOWN";
        String uri = "UNKNOWN";
        String controllerName = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        
        try {
            request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            method = request.getMethod();
            uri = request.getRequestURI();
            String queryString = request.getQueryString();
            String fullUri = queryString != null ? uri + "?" + queryString : uri;
            
            // MDC에 추가 정보 설정
            MDC.put("method", method);
            MDC.put("uri", uri);
            MDC.put("controller", controllerName);
            MDC.put("handler", methodName);
            
            // 파라미터 마스킹 처리
            String params = maskSensitiveParams(joinPoint.getArgs());

            log.info("[REQ] -> {} {} | Controller: {}.{}() | Params: {}", 
                    method, fullUri, controllerName, methodName, params);

            Object result = joinPoint.proceed(); // 실제 메서드 실행

            long executionTime = System.currentTimeMillis() - startTime;
            MDC.put("executionTime", String.valueOf(executionTime));
            log.info("[RES] <- {} {} | Time: {}ms", method, uri, executionTime);
            
            return result;

        } catch (Throwable e) {
            long executionTime = System.currentTimeMillis() - startTime;
            MDC.put("executionTime", String.valueOf(executionTime));
            MDC.put("error", e.getClass().getSimpleName());
            
            // 예외 발생 시, 어떤 Controller에서 발생했는지 명시적으로 남김
            String errorUri = (request != null) ? request.getRequestURI() : uri;
            String errorMethod = (request != null) ? request.getMethod() : method;
            
            log.error("[ERR] <- {} {} | Controller: {}.{}() | Time: {}ms | Exception: {} | Message: {}", 
                    errorMethod, errorUri, controllerName, methodName, 
                    executionTime, e.getClass().getSimpleName(), e.getMessage(), e);
            
            // 여기서 e를 다시 던져야 GlobalExceptionHandler가 잡아서 클라이언트에게 응답을 보낼 수 있음
            throw e;
        } finally {
            // MDC 정리 (메모리 누수 방지)
            MDC.clear();
        }
    }
    
    /**
     * 파라미터에서 민감한 정보를 마스킹합니다.
     */
    private String maskSensitiveParams(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        
        String paramsString = Arrays.toString(args);
        return SensitiveDataMaskingFilter.maskSensitiveData(paramsString);
    }
}
