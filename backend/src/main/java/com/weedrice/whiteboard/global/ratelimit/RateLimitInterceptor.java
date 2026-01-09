package com.weedrice.whiteboard.global.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiting 인터셉터
 * 
 * API 요청에 대해 Rate Limiting을 적용합니다.
 * - IP 기반: 기본 제한
 * - 사용자 기반: 인증된 사용자는 더 높은 제한
 * - 엔드포인트별: 인증 엔드포인트는 더 엄격한 제한
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final Bucket defaultBucket;
    private final Bucket authBucket;
    private final Bucket apiBucket;
    private final Map<String, Bucket> userBuckets;
    private final RateLimitConfig rateLimitConfig;
    private final ObjectMapper objectMapper;

    // IP 기반 버킷 저장소
    private final Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, 
                             @NonNull HttpServletResponse response, 
                             @NonNull Object handler) throws Exception {
        
        String path = request.getRequestURI();
        
        // Rate Limiting 제외 경로
        if (shouldSkipRateLimit(path)) {
            return true;
        }

        Bucket bucket = resolveBucket(request, path);
        
        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for path: {}, IP: {}", path, getClientIp(request));
            sendRateLimitError(response);
            return false;
        }

        return true;
    }

    /**
     * Rate Limiting을 적용할 버킷 결정
     */
    private Bucket resolveBucket(HttpServletRequest request, String path) {
        // 인증 엔드포인트는 더 엄격한 제한
        if (path.startsWith("/api/v1/auth/")) {
            return authBucket;
        }

        // 인증된 사용자는 사용자별 버킷 사용
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            Long userId = userDetails.getUserId();
            
            return userBuckets.computeIfAbsent(
                "user:" + userId,
                k -> rateLimitConfig.createUserBucket()
            );
        }

        // IP 기반 버킷 사용
        String clientIp = getClientIp(request);
        return ipBuckets.computeIfAbsent(
            clientIp,
            k -> apiBucket
        );
    }

    /**
     * Rate Limiting 제외 경로 확인
     */
    private boolean shouldSkipRateLimit(String path) {
        // Health check, Actuator, Swagger 등은 제외
        return path.startsWith("/actuator/") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/api-docs") ||
               path.startsWith("/uploads/");
    }

    /**
     * 클라이언트 IP 주소 추출
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For는 여러 IP가 콤마로 구분될 수 있음
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    /**
     * Rate Limit 초과 시 에러 응답 전송
     */
    private void sendRateLimitError(HttpServletResponse response) throws IOException {
        response.setStatus(429); // 429 Too Many Requests
        response.setContentType("application/json;charset=UTF-8");
        
        ApiResponse<?> errorResponse = ApiResponse.error(
            ErrorCode.RATE_LIMIT_EXCEEDED.getCode(),
            "Too many requests. Please try again later."
        );
        
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
