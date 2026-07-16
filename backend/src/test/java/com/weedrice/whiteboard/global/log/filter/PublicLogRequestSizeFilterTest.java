package com.weedrice.whiteboard.global.log.filter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.context.support.StaticMessageSource;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PublicLogRequestSizeFilterTest {

    private static final int MAX_BODY_BYTES = PublicLogRequestSizeFilter.MAX_REQUEST_BODY_BYTES;

    private ObjectMapper objectMapper;
    private PublicLogRequestSizeFilter filter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage(
                "validation.publicLog.body.max", Locale.ENGLISH,
                "Public log collection request body must not exceed 32 KiB.");
        filter = new PublicLogRequestSizeFilter(objectMapper, messageSource);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/logs/client",
            "/api/v1/security/csp-report"
    })
    @DisplayName("공개 로그 body가 제한 경계와 같으면 캐시된 body로 필터 체인에 전달한다")
    void exactMaxBody_reachesFilterChainWithCachedBody(String path) throws Exception {
        byte[] body = "a".repeat(MAX_BODY_BYTES)
                .getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = request("POST", path, body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<HttpServletRequest> downstreamRequest = new AtomicReference<>();
        AtomicReference<byte[]> downstreamBody = new AtomicReference<>();
        FilterChain chain = (filteredRequest, filteredResponse) -> {
            downstreamRequest.set((HttpServletRequest) filteredRequest);
            downstreamBody.set(filteredRequest.getInputStream().readAllBytes());
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(downstreamRequest.get()).isNotSameAs(request);
        assertThat(downstreamRequest.get().getContentLength()).isEqualTo(body.length);
        assertThat(downstreamBody.get()).containsExactly(body);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/logs/client",
            "/api/v1/security/csp-report"
    })
    @DisplayName("공개 로그 body가 제한을 넘으면 413을 반환한다")
    void bodyOverMax_returnsPayloadTooLarge(String path) throws Exception {
        byte[] body = "a".repeat(MAX_BODY_BYTES + 1)
                .getBytes(StandardCharsets.UTF_8);
        assertPayloadTooLarge(path, body);
    }

    @Test
    @DisplayName("CSP report는 문자 수가 작아도 UTF-8 byte 제한을 넘으면 413을 반환한다")
    void multibyteCspBodyOverMax_returnsPayloadTooLarge() throws Exception {
        byte[] body = "가".repeat(11_000).getBytes(StandardCharsets.UTF_8);

        assertThat(body.length).isGreaterThan(MAX_BODY_BYTES);
        assertPayloadTooLarge("/api/v1/security/csp-report", body);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/logs/client;source=browser",
            "/api/v1/security/csp-report;v=1"
    })
    void matrixParameters_doNotBypassLimit(String path) throws Exception {
        assertPayloadTooLarge(path, "a".repeat(MAX_BODY_BYTES + 1).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void contextPath_doesNotBypassLimit() throws Exception {
        byte[] body = "a".repeat(MAX_BODY_BYTES + 1).getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = request("POST", "/noviis/api/v1/logs/client", body);
        request.setContextPath("/noviis");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        verifyNoInteractions(chain);
    }

    private void assertPayloadTooLarge(String path, byte[] body) throws Exception {
        MockHttpServletRequest request = request("POST", path, body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode responseBody = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(responseBody.get("success").asBoolean()).isFalse();
        assertThat(responseBody.at("/error/code").asString()).isEqualTo("C008");
        assertThat(responseBody.at("/error/message").asString()).contains("32 KiB");
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("non-target path and method bypass the request size filter")
    void nonTargetRequests_bypassFilter() throws Exception {
        byte[] oversizedBody = "a".repeat(MAX_BODY_BYTES + 1)
                .getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest otherPath = request("POST", "/api/v1/logs/other", oversizedBody);
        MockHttpServletResponse otherPathResponse = new MockHttpServletResponse();
        FilterChain otherPathChain = mock(FilterChain.class);
        MockHttpServletRequest otherMethod = request("GET", "/api/v1/logs/client", oversizedBody);
        MockHttpServletResponse otherMethodResponse = new MockHttpServletResponse();
        FilterChain otherMethodChain = mock(FilterChain.class);

        filter.doFilter(otherPath, otherPathResponse, otherPathChain);
        filter.doFilter(otherMethod, otherMethodResponse, otherMethodChain);

        verify(otherPathChain).doFilter(same(otherPath), same(otherPathResponse));
        verify(otherMethodChain).doFilter(same(otherMethod), same(otherMethodResponse));
        assertThat(otherPathResponse.getStatus()).isEqualTo(200);
        assertThat(otherMethodResponse.getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest request(String method, String requestUri, byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, requestUri);
        request.setContent(body);
        return request;
    }
}
