package com.weedrice.whiteboard.global.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.weedrice.whiteboard.global.common.util.ClientIpResolver;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private RateLimitConfig rateLimitConfig;

    @Mock
    private MessageSource messageSource;

    @Mock
    private ClientIpResolver clientIpResolver;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증 API rate limit은 resolver가 반환한 IP를 bucket key로 사용한다")
    void preHandle_authApiUsesResolvedClientIpBucket() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                new ConcurrentHashMap<>(),
                rateLimitConfig,
                new ObjectMapper(),
                messageSource,
                clientIpResolver,
                new RateLimitProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();

        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.10");
        when(rateLimitConfig.createAuthBucket()).thenReturn(oneRequestBucket());
        when(rateLimitConfig.getAuthLimit()).thenReturn(1);
        when(messageSource.getMessage(eq("error.common.rateLimitExceeded"), isNull(), any(Locale.class)))
                .thenReturn("Too many requests");

        boolean firstResult = interceptor.preHandle(request, firstResponse, new Object());
        boolean secondResult = interceptor.preHandle(request, secondResponse, new Object());

        assertThat(firstResult).isTrue();
        assertThat(secondResult).isFalse();
        assertThat(secondResponse.getStatus()).isEqualTo(429);
        assertThat(firstResponse.getHeader(RateLimitHeaderWriter.HEADER_LIMIT)).isEqualTo("1");
        assertThat(firstResponse.getHeader(RateLimitHeaderWriter.HEADER_REMAINING)).isEqualTo("0");
        assertThat(secondResponse.getHeader(RateLimitHeaderWriter.HEADER_LIMIT)).isEqualTo("1");
        assertThat(secondResponse.getHeader(RateLimitHeaderWriter.HEADER_REMAINING)).isEqualTo("0");
        assertThat(secondResponse.getHeader(RateLimitHeaderWriter.HEADER_RETRY_AFTER)).isNotBlank();
        verify(clientIpResolver, times(2)).resolve(request);
        verify(rateLimitConfig).createAuthBucket();
    }

    @Test
    void constructor_usesConfiguredIpBucketCacheSettings() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setBucketCacheMaxSize(7);
        properties.setBucketCacheTtlMinutes(13);
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                new ConcurrentHashMap<>(),
                rateLimitConfig,
                new ObjectMapper(),
                messageSource,
                clientIpResolver,
                properties);

        @SuppressWarnings("unchecked")
        Cache<String, Bucket> ipBucketCache = (Cache<String, Bucket>) ReflectionTestUtils.getField(
                interceptor,
                "ipBucketCache");

        assertThat(ipBucketCache.policy().eviction()).isPresent();
        assertThat(ipBucketCache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(7);
        assertThat(ipBucketCache.policy().expireAfterAccess()).isPresent();
        assertThat(ipBucketCache.policy().expireAfterAccess().orElseThrow().getExpiresAfter(TimeUnit.MINUTES))
                .isEqualTo(13);
    }

    private Bucket oneRequestBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(1, Refill.greedy(1, Duration.ofMinutes(1))))
                .build();
    }
}
