package com.weedrice.whiteboard.domain.search.semantic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Slf4j
@Component
class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final SemanticSearchProperties properties;
    private final RestClient restClient;
    private final Clock clock;

    @Autowired
    OpenAiEmbeddingClient(SemanticSearchProperties properties) {
        this(properties, RestClient.builder().requestFactory(requestFactory()), Clock.systemUTC());
    }

    OpenAiEmbeddingClient(SemanticSearchProperties properties, RestClient.Builder builder, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.restClient = builder
                .baseUrl(properties.getOpenai().getBaseUrl())
                .build();
    }

    @Override
    public boolean isAvailable() {
        String apiKey = properties.resolveOpenAiApiKey();
        return properties.isEnabled()
                && "openai".equalsIgnoreCase(properties.getProvider())
                && apiKey != null
                && !apiKey.isBlank();
    }

    @Override
    public float[] embed(String input) {
        if (!isAvailable()) {
            throw new EmbeddingFailureException("UNAVAILABLE", true, null);
        }
        String apiKey = properties.resolveOpenAiApiKey();
        EmbeddingResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/embeddings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(new EmbeddingRequest(properties.getOpenai().getModel(), SemanticSearchEmbeddingInput.limit(input)))
                    .retrieve()
                    .body(EmbeddingResponse.class);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            boolean retryable = status == 408 || status == 409 || status == 425 || status == 429 || status >= 500;
            String code = status == 429 ? "RATE_LIMITED" : "HTTP_" + status;
            log.warn("OpenAI embedding request failed. code={}", code);
            String retryAfter = ex.getResponseHeaders() == null ? null
                    : ex.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
            throw new EmbeddingFailureException(code, retryable, parseRetryAfter(retryAfter));
        } catch (ResourceAccessException ex) {
            log.warn("OpenAI embedding request failed. exceptionType={}", ex.getClass().getSimpleName());
            throw new EmbeddingFailureException("NETWORK", true, null);
        } catch (RestClientException ex) {
            throw new EmbeddingFailureException("INVALID_RESPONSE", false, null);
        }

        if (response == null || response.data() == null || response.data().isEmpty()
                || response.data().size() != 1 || response.data().get(0) == null
                || response.data().get(0).embedding() == null) {
            log.warn("OpenAI embedding response did not contain an embedding");
            throw new EmbeddingFailureException("INVALID_RESPONSE", false, null);
        }
        List<Double> values = response.data().get(0).embedding();
        if (values.size() != properties.getEmbeddingDimension()) {
            log.warn("OpenAI embedding dimension mismatch. expected={}, actual={}",
                    properties.getEmbeddingDimension(), values.size());
            throw new EmbeddingFailureException("DIMENSION_MISMATCH", false, null);
        }
        float[] embedding = new float[values.size()];
        boolean nonzero = false;
        for (int i = 0; i < values.size(); i++) {
            Double value = values.get(i);
            if (value == null || !Double.isFinite(value) || !Float.isFinite(value.floatValue())) {
                throw new EmbeddingFailureException("INVALID_VECTOR", false, null);
            }
            embedding[i] = value.floatValue();
            nonzero |= embedding[i] != 0;
        }
        if (!nonzero) throw new EmbeddingFailureException("ZERO_VECTOR", false, null);
        return embedding;
    }

    private Duration parseRetryAfter(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value.trim())));
        } catch (NumberFormatException ignored) {
            try {
                Duration duration = Duration.between(clock.instant(),
                        ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                return duration.isNegative() ? Duration.ZERO : duration;
            } catch (DateTimeParseException invalidDate) {
                return null;
            }
        }
    }

    @Override
    public String model() {
        return properties.getOpenai().getModel();
    }

    private record EmbeddingRequest(String model, String input) {
    }

    private record EmbeddingResponse(List<EmbeddingData> data) {
    }

    private record EmbeddingData(List<Double> embedding) {
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}
