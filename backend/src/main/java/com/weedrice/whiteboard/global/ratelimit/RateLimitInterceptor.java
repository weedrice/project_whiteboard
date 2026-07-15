package com.weedrice.whiteboard.global.ratelimit;

import tools.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.util.ClientIpResolver;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final Set<String> STRICT_AUTH_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/api/v1/auth/password/send-reset-link",
            "/api/v1/auth/password/send-reset-link-by-email",
            "/api/v1/auth/password/reset",
            "/api/v1/auth/password/reset-by-code");

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
        BucketResolution resolution = resolveBucket(resolveRateLimitPath(request, path), clientIp);
        ConsumptionProbe probe = resolution.bucket().tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long retryAfterSeconds = RateLimitHeaderWriter.secondsUntilRefill(probe.getNanosToWaitForRefill());
            RateLimitHeaderWriter.write(response, RateLimitSnapshot.rejected(
                    resolution.limit(),
                    Math.max(0, probe.getRemainingTokens()),
                    retryAfterSeconds));
            log.debug("Rate limit exceeded for path: {}, IP: {}", path, clientIp);
            sendRateLimitError(request, response);
            return false;
        }
        RateLimitHeaderWriter.write(response, RateLimitSnapshot.allowed(
                resolution.limit(),
                Math.max(0, probe.getRemainingTokens()),
                RateLimitHeaderWriter.secondsUntilRefill(probe.getNanosToWaitForReset())));
        return true;
    }

    private BucketResolution resolveBucket(String path, String clientIp) {
        if (REFRESH_PATH.equals(path)) {
            return resolveApiBucket(clientIp);
        }

        if (STRICT_AUTH_PATHS.contains(path)) {
            String authIpKey = "auth:" + clientIp;
            return new BucketResolution(
                    ipBucketCache.asMap().computeIfAbsent(authIpKey, k -> rateLimitConfig.createAuthBucket()),
                    rateLimitConfig.getAuthLimit());
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            Long userId = userDetails.getUserId();
            return new BucketResolution(
                    userBuckets.computeIfAbsent("user:" + userId, k -> rateLimitConfig.createUserBucket()),
                    rateLimitConfig.getUserLimit());
        }

        return resolveApiBucket(clientIp);
    }

    private BucketResolution resolveApiBucket(String clientIp) {
        return new BucketResolution(
                ipBucketCache.asMap().computeIfAbsent("api:" + clientIp, k -> rateLimitConfig.createApiBucket()),
                rateLimitConfig.getApiLimit());
    }

    private String resolveRateLimitPath(HttpServletRequest request, String requestPath) {
        Object matchingPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return matchingPattern != null ? matchingPattern.toString() : requestPath;
    }

    private boolean shouldSkipRateLimit(String path) {
        return path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/api-docs");
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

    private record BucketResolution(Bucket bucket, long limit) {
    }
}
