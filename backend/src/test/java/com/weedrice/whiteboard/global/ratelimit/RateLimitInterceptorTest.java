package com.weedrice.whiteboard.global.ratelimit;

import tools.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.weedrice.whiteboard.global.common.util.ClientIpResolver;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
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

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/api/v1/auth/password/send-reset-link",
            "/api/v1/auth/password/send-reset-link-by-email",
            "/api/v1/auth/password/reset",
            "/api/v1/auth/password/reset-by-code"
    })
    @DisplayName("strict 경로는 auth 버킷을 사용한다")
    void preHandle_strictPathUsesAuthBucket(String path) throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                new ConcurrentHashMap<>(),
                rateLimitConfig,
                new ObjectMapper(),
                messageSource,
                clientIpResolver,
                new RateLimitProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.11");
        when(rateLimitConfig.createAuthBucket()).thenReturn(oneRequestBucket());
        when(rateLimitConfig.getAuthLimit()).thenReturn(5);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(response.getHeader(RateLimitHeaderWriter.HEADER_LIMIT)).isEqualTo("5");
        verify(rateLimitConfig).createAuthBucket();
        verify(rateLimitConfig, never()).createApiBucket();
    }

    @Test
    @DisplayName("공개 로그 경로는 인증 여부와 무관하게 IP별 전용 버킷을 공유한다")
    void preHandle_publicLogPathsShareDedicatedIpBucketEvenWhenAuthenticated() throws Exception {
        RateLimitInterceptor interceptor = newInterceptor(new RateLimitProperties());
        MockHttpServletRequest clientLogRequest =
                new MockHttpServletRequest("POST", "/api/v1/logs/client");
        MockHttpServletRequest cspReportRequest =
                new MockHttpServletRequest("POST", "/api/v1/security/csp-report");
        MockHttpServletResponse clientLogResponse = new MockHttpServletResponse();
        MockHttpServletResponse cspReportResponse = new MockHttpServletResponse();
        CustomUserDetails userDetails = org.mockito.Mockito.mock(CustomUserDetails.class);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, List.of());

        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(clientIpResolver.resolve(clientLogRequest)).thenReturn("203.0.113.16");
        when(clientIpResolver.resolve(cspReportRequest)).thenReturn("203.0.113.16");
        when(rateLimitConfig.createPublicLogBucket()).thenReturn(oneRequestBucket());
        when(rateLimitConfig.getPublicLogLimit()).thenReturn(10);
        when(messageSource.getMessage(eq("error.common.rateLimitExceeded"), isNull(), any(Locale.class)))
                .thenReturn("Too many requests");

        boolean clientLogResult = interceptor.preHandle(clientLogRequest, clientLogResponse, new Object());
        boolean cspReportResult = interceptor.preHandle(cspReportRequest, cspReportResponse, new Object());

        assertThat(clientLogResult).isTrue();
        assertThat(cspReportResult).isFalse();
        assertThat(clientLogResponse.getHeader(RateLimitHeaderWriter.HEADER_LIMIT)).isEqualTo("10");
        assertThat(cspReportResponse.getStatus()).isEqualTo(429);
        assertThat(cspReportResponse.getHeader(RateLimitHeaderWriter.HEADER_LIMIT)).isEqualTo("10");
        verify(rateLimitConfig).createPublicLogBucket();
        verify(rateLimitConfig, never()).createUserBucket();
        verify(rateLimitConfig, never()).createApiBucket();
        verify(rateLimitConfig, never()).createAuthBucket();
    }

    @Test
    @DisplayName("에이전트 등록 5회 한도는 같은 IP의 auth 버킷과 독립적이다")
    void preHandle_agentRegisterBucketIsIndependentFromAuthBucket() throws Exception {
        RateLimitProperties properties = new RateLimitProperties();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                new RateLimitBucketStore(properties, registry),
                rateLimitConfig,
                new ObjectMapper(),
                messageSource,
                clientIpResolver,
                registry);
        MockHttpServletRequest register = new MockHttpServletRequest("POST", "/api/v1/agents/register");
        MockHttpServletRequest login = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        when(clientIpResolver.resolve(register)).thenReturn("203.0.113.19");
        when(clientIpResolver.resolve(login)).thenReturn("203.0.113.19");
        when(rateLimitConfig.createAgentRegisterBucket()).thenReturn(requestBucket(5));
        when(rateLimitConfig.getAgentRegisterLimit()).thenReturn(5);
        when(rateLimitConfig.createAuthBucket()).thenReturn(oneRequestBucket());
        when(rateLimitConfig.getAuthLimit()).thenReturn(5);
        when(messageSource.getMessage(eq("error.common.rateLimitExceeded"), isNull(), any(Locale.class)))
                .thenReturn("Too many requests");

        for (int requestCount = 0; requestCount < 5; requestCount++) {
            assertThat(interceptor.preHandle(register, new MockHttpServletResponse(), new Object())).isTrue();
        }
        assertThat(interceptor.preHandle(register, new MockHttpServletResponse(), new Object())).isFalse();
        assertThat(interceptor.preHandle(login, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(registry.counter("noviis.ratelimit.rejected", "tier", "agent-register").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("matrix parameter가 포함된 민감 인증 API도 매칭 패턴 기준 auth 버킷을 사용한다")
    void preHandle_sensitiveAuthApiWithMatrixParameterUsesAuthBucket() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                new ConcurrentHashMap<>(),
                rateLimitConfig,
                new ObjectMapper(),
                messageSource,
                clientIpResolver,
                new RateLimitProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login;variant=1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/auth/login");

        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.15");
        when(rateLimitConfig.createAuthBucket()).thenReturn(oneRequestBucket());
        when(rateLimitConfig.getAuthLimit()).thenReturn(5);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(response.getHeader(RateLimitHeaderWriter.HEADER_LIMIT)).isEqualTo("5");
        verify(rateLimitConfig).createAuthBucket();
        verify(rateLimitConfig, never()).createApiBucket();
    }

    @Test
    @DisplayName("토큰 갱신은 인증 상태와 무관하게 API IP 버킷을 사용한다")
    void preHandle_refreshUsesApiBucketEvenWhenAuthenticated() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                new ConcurrentHashMap<>(),
                rateLimitConfig,
                new ObjectMapper(),
                messageSource,
                clientIpResolver,
                new RateLimitProperties());
        MockHttpServletRequest refreshRequest = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        MockHttpServletRequest apiRequest = new MockHttpServletRequest("GET", "/api/v1/posts");
        MockHttpServletResponse refreshResponse = new MockHttpServletResponse();
        MockHttpServletResponse apiResponse = new MockHttpServletResponse();
        CustomUserDetails userDetails = org.mockito.Mockito.mock(CustomUserDetails.class);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, List.of());

        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(clientIpResolver.resolve(refreshRequest)).thenReturn("203.0.113.12");
        when(clientIpResolver.resolve(apiRequest)).thenReturn("203.0.113.12");
        when(rateLimitConfig.createApiBucket()).thenReturn(oneRequestBucket());
        when(rateLimitConfig.getApiLimit()).thenReturn(200);
        when(messageSource.getMessage(eq("error.common.rateLimitExceeded"), isNull(), any(Locale.class)))
                .thenReturn("Too many requests");

        boolean refreshResult = interceptor.preHandle(refreshRequest, refreshResponse, new Object());
        SecurityContextHolder.clearContext();
        boolean apiResult = interceptor.preHandle(apiRequest, apiResponse, new Object());

        assertThat(refreshResult).isTrue();
        assertThat(apiResult).isFalse();
        assertThat(apiResponse.getStatus()).isEqualTo(429);
        assertThat(refreshResponse.getHeader(RateLimitHeaderWriter.HEADER_LIMIT)).isEqualTo("200");
        verify(rateLimitConfig, times(1)).createApiBucket();
        verify(rateLimitConfig, never()).createAuthBucket();
        verify(rateLimitConfig, never()).createUserBucket();
    }

    @Test
    @DisplayName("비민감 auth API는 익명 요청일 때 API IP 버킷을 사용한다")
    void preHandle_nonSensitiveAuthApiUsesApiBucket() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                new ConcurrentHashMap<>(),
                rateLimitConfig,
                new ObjectMapper(),
                messageSource,
                clientIpResolver,
                new RateLimitProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/email/verify");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.13");
        when(rateLimitConfig.createApiBucket()).thenReturn(oneRequestBucket());
        when(rateLimitConfig.getApiLimit()).thenReturn(200);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(response.getHeader(RateLimitHeaderWriter.HEADER_LIMIT)).isEqualTo("200");
        verify(rateLimitConfig).createApiBucket();
        verify(rateLimitConfig, never()).createAuthBucket();
    }

    @Test
    @DisplayName("비민감 auth API는 인증 요청일 때 사용자 버킷을 사용한다")
    void preHandle_authenticatedNonSensitiveAuthApiUsesUserBucket() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                new ConcurrentHashMap<>(),
                rateLimitConfig,
                new ObjectMapper(),
                messageSource,
                clientIpResolver,
                new RateLimitProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CustomUserDetails userDetails = org.mockito.Mockito.mock(CustomUserDetails.class);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, List.of());

        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.14");
        when(userDetails.getUserId()).thenReturn(7L);
        when(rateLimitConfig.createUserBucket()).thenReturn(oneRequestBucket());
        when(rateLimitConfig.getUserLimit()).thenReturn(500);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(response.getHeader(RateLimitHeaderWriter.HEADER_LIMIT)).isEqualTo("500");
        verify(rateLimitConfig).createUserBucket();
        verify(rateLimitConfig, never()).createAuthBucket();
        verify(rateLimitConfig, never()).createApiBucket();
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
        return requestBucket(1);
    }

    private Bucket requestBucket(int capacity) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private RateLimitInterceptor newInterceptor(RateLimitProperties properties) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        return new RateLimitInterceptor(
                new RateLimitBucketStore(properties, registry),
                rateLimitConfig,
                new ObjectMapper(),
                messageSource,
                clientIpResolver,
                registry);
    }
}
