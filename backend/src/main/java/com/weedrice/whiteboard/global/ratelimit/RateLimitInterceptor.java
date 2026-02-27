package com.weedrice.whiteboard.global.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final Map<String, Bucket> userBuckets;
    private final RateLimitConfig rateLimitConfig;
    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;
    private final RateLimitProperties rateLimitProperties;

    // Bounded IP bucket cache to prevent unbounded memory usage.
    private final Map<String, Bucket> ipBuckets = Caffeine.newBuilder()
            .maximumSize(20_000)
            .expireAfterAccess(Duration.ofHours(2))
            .<String, Bucket>build()
            .asMap();

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) throws Exception {

        String path = request.getRequestURI();
        if (shouldSkipRateLimit(path)) {
            return true;
        }

        Bucket bucket = resolveBucket(request, path);
        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for path: {}, IP: {}", path, getClientIp(request));
            sendRateLimitError(request, response);
            return false;
        }
        return true;
    }

    private Bucket resolveBucket(HttpServletRequest request, String path) {
        if (path.startsWith("/api/v1/auth/")
                && !"/api/v1/auth/refresh".equals(path)
                && !"/api/v1/auth/logout".equals(path)) {
            String authIpKey = "auth:" + getClientIp(request);
            return ipBuckets.computeIfAbsent(authIpKey, k -> rateLimitConfig.createAuthBucket());
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            Long userId = userDetails.getUserId();
            return userBuckets.computeIfAbsent("user:" + userId, k -> rateLimitConfig.createUserBucket());
        }

        String clientIp = getClientIp(request);
        return ipBuckets.computeIfAbsent("api:" + clientIp, k -> rateLimitConfig.createApiBucket());
    }

    private boolean shouldSkipRateLimit(String path) {
        return path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/api-docs")
                || path.startsWith("/uploads/");
    }

    private String getClientIp(HttpServletRequest request) {
        if (!rateLimitProperties.isTrustProxyHeaders()) {
            return request.getRemoteAddr();
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            String[] ips = xForwardedFor.split(",");
            for (String ip : ips) {
                String candidate = ip == null ? null : ip.trim();
                if (candidate != null && !candidate.isEmpty() && !"unknown".equalsIgnoreCase(candidate)) {
                    return candidate;
                }
            }
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    private void sendRateLimitError(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");

        Locale locale = request.getLocale() != null ? request.getLocale() : Locale.getDefault();
        String message = messageSource.getMessage("error.common.rateLimitExceeded", null, locale);

        ApiResponse<?> errorResponse = ApiResponse.error(
                ErrorCode.RATE_LIMIT_EXCEEDED.getCode(),
                message);

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
