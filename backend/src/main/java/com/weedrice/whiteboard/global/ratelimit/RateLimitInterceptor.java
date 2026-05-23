package com.weedrice.whiteboard.global.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.util.ClientIpResolver;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
public class RateLimitInterceptor implements HandlerInterceptor {

    private final Map<String, Bucket> userBuckets;
    private final RateLimitConfig rateLimitConfig;
    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;
    private final ClientIpResolver clientIpResolver;
    private final Cache<String, Bucket> ipBucketCache;

    public RateLimitInterceptor(
            Map<String, Bucket> userBuckets,
            RateLimitConfig rateLimitConfig,
            ObjectMapper objectMapper,
            MessageSource messageSource,
            ClientIpResolver clientIpResolver,
            RateLimitProperties properties) {
        this.userBuckets = userBuckets;
        this.rateLimitConfig = rateLimitConfig;
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
        this.clientIpResolver = clientIpResolver;
        this.ipBucketCache = Caffeine.newBuilder()
                .maximumSize(properties.getBucketCacheMaxSize())
                .expireAfterAccess(Duration.ofMinutes(properties.getBucketCacheTtlMinutes()))
                .<String, Bucket>build();
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) throws Exception {

        String path = request.getRequestURI();
        if (shouldSkipRateLimit(path)) {
            return true;
        }

        String clientIp = clientIpResolver.resolve(request);
        Bucket bucket = resolveBucket(path, clientIp);
        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for path: {}, IP: {}", path, clientIp);
            sendRateLimitError(request, response);
            return false;
        }
        return true;
    }

    private Bucket resolveBucket(String path, String clientIp) {
        if (path.startsWith("/api/v1/auth/")
                && !"/api/v1/auth/refresh".equals(path)
                && !"/api/v1/auth/logout".equals(path)) {
            String authIpKey = "auth:" + clientIp;
            return ipBucketCache.asMap().computeIfAbsent(authIpKey, k -> rateLimitConfig.createAuthBucket());
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            Long userId = userDetails.getUserId();
            return userBuckets.computeIfAbsent("user:" + userId, k -> rateLimitConfig.createUserBucket());
        }

        return ipBucketCache.asMap().computeIfAbsent("api:" + clientIp, k -> rateLimitConfig.createApiBucket());
    }

    private boolean shouldSkipRateLimit(String path) {
        return path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/api-docs")
                || path.startsWith("/uploads/");
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
