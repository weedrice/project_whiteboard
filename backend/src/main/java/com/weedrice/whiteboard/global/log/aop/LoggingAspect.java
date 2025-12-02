package com.weedrice.whiteboard.global.log.aop;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
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
        
        // Request 정보 가져오기
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String controllerName = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String params = Arrays.toString(joinPoint.getArgs());

        log.info("[REQ:{}] -> {} {} | Controller: {}.{}() | Params: {}", 
                requestId, method, uri, controllerName, methodName, params);

        try {
            Object result = joinPoint.proceed(); // 실제 메서드 실행

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("[RES:{}] <- {} {} | Time: {}ms", requestId, method, uri, executionTime);
            
            return result;

        } catch (Throwable e) {
            long executionTime = System.currentTimeMillis() - startTime;
            // 예외 발생 시, 어떤 Controller에서 발생했는지 명시적으로 남김
            log.error("[ERR:{}] <- {} {} | Controller: {}.{}() | Time: {}ms | Exception: {} | Message: {}", 
                    requestId, method, uri, controllerName, methodName, executionTime, e.getClass().getSimpleName(), e.getMessage());
            
            // 여기서 e를 다시 던져야 GlobalExceptionHandler가 잡아서 클라이언트에게 응답을 보낼 수 있음
            throw e;
        }
    }
}
