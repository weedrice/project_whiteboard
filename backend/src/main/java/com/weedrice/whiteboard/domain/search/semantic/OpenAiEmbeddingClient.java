package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
class OpenAiEmbeddingClient implements EmbeddingClient {

    private final SemanticSearchProperties properties;

    OpenAiEmbeddingClient(SemanticSearchProperties properties) {
        this.properties = properties;
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
        EmbeddingResponse response = RestClient.builder()
                .baseUrl(properties.getOpenai().getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build()
                .post()
                .uri("/v1/embeddings")
                .body(new EmbeddingRequest(properties.getOpenai().getModel(), input))
                .retrieve()
                .body(EmbeddingResponse.class);

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
}
