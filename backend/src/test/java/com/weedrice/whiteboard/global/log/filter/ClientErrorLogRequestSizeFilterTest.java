package com.weedrice.whiteboard.global.log.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ClientErrorLogRequestSizeFilterTest {

    private static final int MAX_BODY_BYTES = ClientErrorLogRequestSizeFilter.MAX_REQUEST_BODY_BYTES;

    private ObjectMapper objectMapper;
    private ClientErrorLogRequestSizeFilter filter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new ClientErrorLogRequestSizeFilter(objectMapper);
    }

    @Test
    @DisplayName("client error log body at the configured boundary reaches the filter chain")
    void exactMaxBody_reachesFilterChainWithCachedBody() throws Exception {
        byte[] body = "a".repeat(MAX_BODY_BYTES)
                .getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = request("POST", "/api/v1/logs/client", body);
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

    @Test
    @DisplayName("client error log body over the configured limit is rejected with 413")
    void bodyOverMax_returnsPayloadTooLarge() throws Exception {
        byte[] body = "a".repeat(MAX_BODY_BYTES + 1)
                .getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = request("POST", "/api/v1/logs/client", body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode responseBody = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(responseBody.get("success").asBoolean()).isFalse();
        assertThat(responseBody.at("/error/code").asText()).isEqualTo("C008");
        assertThat(responseBody.at("/error/message").asText()).contains("32 KiB");
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
