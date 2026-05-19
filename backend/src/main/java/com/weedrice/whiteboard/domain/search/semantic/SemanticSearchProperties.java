package com.weedrice.whiteboard.domain.search.semantic;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "semantic-search")
public class SemanticSearchProperties {
    private static final String REQUIRED_PROVIDER = "openai";
    private static final String REQUIRED_OPENAI_MODEL = "text-embedding-3-small";
    private static final int REQUIRED_EMBEDDING_DIMENSION = 1536;

    private boolean enabled = false;
    private String provider = REQUIRED_PROVIDER;
    private int embeddingDimension = REQUIRED_EMBEDDING_DIMENSION;
    private OpenAi openai = new OpenAi();
    private Job job = new Job();

    @PostConstruct
    void validateContract() {
        if (!enabled) {
            return;
        }
        if (!REQUIRED_PROVIDER.equalsIgnoreCase(provider)) {
            throw new IllegalStateException("semantic-search.provider must be openai when semantic search is enabled");
        }
        if (!REQUIRED_OPENAI_MODEL.equals(openai.model)) {
            throw new IllegalStateException(
                    "semantic-search.openai.model must be text-embedding-3-small when semantic search is enabled");
        }
        if (embeddingDimension != REQUIRED_EMBEDDING_DIMENSION) {
            throw new IllegalStateException(
                    "semantic-search.embedding-dimension must be 1536 when semantic search is enabled");
        }
    }

    public String resolveOpenAiApiKey() {
        if (openai.apiKey != null && !openai.apiKey.isBlank()) {
            return openai.apiKey;
        }
        return System.getenv("OPENAI_API_KEY");
    }

    @Getter
    @Setter
    public static class OpenAi {
        private String baseUrl = "https://api.openai.com";
        private String apiKey = "";
        private String model = REQUIRED_OPENAI_MODEL;
    }

    @Getter
    @Setter
    public static class Job {
        private int batchSize = 20;
        private int maxRetryCount = 5;
        private int processingLeaseMinutes = 10;
    }
}
