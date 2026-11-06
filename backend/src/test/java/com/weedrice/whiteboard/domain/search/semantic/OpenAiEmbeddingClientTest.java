package com.weedrice.whiteboard.domain.search.semantic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenAiEmbeddingClientTest {
    private MockRestServiceServer server;
    private OpenAiEmbeddingClient client;

    @BeforeEach
    void setUp() {
        SemanticSearchProperties properties = new SemanticSearchProperties();
        properties.setEnabled(true);
        properties.setEmbeddingDimension(2);
        properties.getOpenai().setApiKey("test-placeholder");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiEmbeddingClient(properties, builder,
                Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void acceptsFiniteNonzeroVector() {
        respond("{\"data\":[{\"embedding\":[0.25,-0.5]}]}");
        assertThat(client.embed("test")).containsExactly(0.25f, -0.5f);
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"400,false", "401,false", "403,false", "404,false", "408,true", "409,true",
            "425,true", "429,true", "500,true", "503,true"})
    void classifiesHttpFailuresWithoutRetainingResponseBody(int status, boolean retryable) {
        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("private upstream details"));
        EmbeddingFailureException failure = failure();
        assertThat(failure.retryable()).isEqualTo(retryable);
        assertThat(failure.code()).isEqualTo(status == 429 ? "RATE_LIMITED" : "HTTP_" + status);
        assertThat(failure.getMessage()).doesNotContain("private");
        assertThat(failure.getCause()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"120,120", "Thu 05 Nov 2026 00:01:00 GMT,60", "Thu 05 Nov 2026 00:00:00 GMT,0",
            "Wed 04 Nov 2026 23:59:00 GMT,0", "-10,0"})
    void preservesRetryAfterSecondsAndHttpDate(String header, long expectedSeconds) {
        if (header.contains("GMT")) header = header.substring(0, 3) + "," + header.substring(3);
        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, header));
        assertThat(failure().retryAfter()).isEqualTo(Duration.ofSeconds(expectedSeconds));
    }

    @Test
    void ignoresMalformedRetryAfter() {
        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "invalid"));
        assertThat(failure().retryAfter()).isNull();
    }

    @Test
    void networkFailureIsRetryableAndSafe() {
        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
                .andRespond(withException(new IOException("private network details")));
        EmbeddingFailureException failure = failure();
        assertThat(failure.retryable()).isTrue();
        assertThat(failure.code()).isEqualTo("NETWORK");
        assertThat(failure.getMessage()).doesNotContain("private");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"data\":[]}", "{\"data\":[null]}",
            "{\"data\":[{\"embedding\":[1]}]}", "{\"data\":[{\"embedding\":[null,1]}]}",
            "{\"data\":[{\"embedding\":[0,0]}]}", "{\"data\":[{\"embedding\":[1e100,1]}]}",
            "{\"data\":[{\"embedding\":[1e-100,0]}]}", "not json"})
    void rejectsMalformedOrUnusableVectors(String body) {
        respond(body);
        assertThat(failure().retryable()).isFalse();
    }

    private void respond(String body) {
        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private EmbeddingFailureException failure() {
        Throwable failure = catchThrowable(() -> client.embed("test"));
        assertThat(failure).isInstanceOf(EmbeddingFailureException.class);
        server.verify();
        return (EmbeddingFailureException) failure;
    }
}
