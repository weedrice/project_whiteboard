package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final SemanticSearchProperties properties;
    private final RestClient restClient;

    OpenAiEmbeddingClient(SemanticSearchProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getOpenai().getBaseUrl())
                .requestFactory(requestFactory())
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
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        String apiKey = properties.resolveOpenAiApiKey();
        EmbeddingResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/embeddings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(new EmbeddingRequest(properties.getOpenai().getModel(), input))
                    .retrieve()
                    .body(EmbeddingResponse.class);
        } catch (RestClientResponseException ex) {
            log.warn("OpenAI embedding request failed. status={}", ex.getStatusCode());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        } catch (RestClientException ex) {
            log.warn("OpenAI embedding request failed: {}", ex.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        if (response == null || response.data() == null || response.data().isEmpty()
                || response.data().get(0).embedding() == null) {
            log.warn("OpenAI embedding response did not contain an embedding");
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        List<Double> values = response.data().get(0).embedding();
        if (values.size() != properties.getEmbeddingDimension()) {
            log.warn("OpenAI embedding dimension mismatch. expected={}, actual={}",
                    properties.getEmbeddingDimension(), values.size());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        float[] embedding = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            embedding[i] = values.get(i).floatValue();
        }
        return embedding;
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

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}
