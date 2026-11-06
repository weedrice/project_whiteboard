package com.weedrice.whiteboard.domain.search.semantic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticSearchPropertiesTest {

    @Test
    void relatedPostMinimumSimilarity_defaultsToPointFiveFive() {
        SemanticSearchProperties properties = new SemanticSearchProperties();

        assertThat(properties.getRelatedPost().getMinSimilarity()).isEqualTo(0.55);
    }

    @Test
    void validateContract_rejectsRelatedPostMinimumSimilarityOutsideCosineRange() {
        SemanticSearchProperties properties = new SemanticSearchProperties();
        properties.getRelatedPost().setMinSimilarity(1.01);

        assertThatThrownBy(properties::validateContract)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("semantic-search.related-post.min-similarity");
    }

    @Test
    void validateContract_allowsDisabledCustomValues() {
        SemanticSearchProperties properties = new SemanticSearchProperties();
        properties.setProvider("other");
        properties.setEmbeddingDimension(3072);
        properties.getOpenai().setModel("other-model");

        assertThatCode(properties::validateContract).doesNotThrowAnyException();
    }

    @Test
    void validateContract_rejectsUnsupportedProviderWhenEnabled() {
        SemanticSearchProperties properties = enabledProperties();
        properties.setProvider("other");

        assertThatThrownBy(properties::validateContract)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("semantic-search.provider");
    }

    @Test
    void validateContract_rejectsUnsupportedModelWhenEnabled() {
        SemanticSearchProperties properties = enabledProperties();
        properties.getOpenai().setModel("text-embedding-3-large");

        assertThatThrownBy(properties::validateContract)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("semantic-search.openai.model");
    }

    @Test
    void validateContract_rejectsUnsupportedDimensionWhenEnabled() {
        SemanticSearchProperties properties = enabledProperties();
        properties.setEmbeddingDimension(3072);

        assertThatThrownBy(properties::validateContract)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("semantic-search.embedding-dimension");
    }

    @Test
    void retrievalDefaultsAreIndependentFromRelatedPostThreshold() {
        SemanticSearchProperties properties = new SemanticSearchProperties();
        assertThat(properties.getRetrieval().getMinSimilarity()).isEqualTo(0.20);
        assertThat(properties.getRetrieval().getCandidateLimit()).isEqualTo(200);
        properties.getRetrieval().setMinSimilarity(0.3);
        assertThat(properties.getRelatedPost().getMinSimilarity()).isEqualTo(0.55);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, -1.01, 1.01})
    void retrievalRejectsInvalidSimilarityEvenWhenDisabled(double value) {
        SemanticSearchProperties properties = new SemanticSearchProperties();
        properties.getRetrieval().setMinSimilarity(value);
        assertThatThrownBy(properties::validateContract).hasMessageContaining("retrieval.min-similarity");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {-1, 0, 19, 1001})
    void retrievalRejectsUnboundedCandidatePools(int value) {
        SemanticSearchProperties properties = new SemanticSearchProperties();
        properties.getRetrieval().setCandidateLimit(value);
        assertThatThrownBy(properties::validateContract).hasMessageContaining("retrieval.candidate-limit");
    }

    private SemanticSearchProperties enabledProperties() {
        SemanticSearchProperties properties = new SemanticSearchProperties();
        properties.setEnabled(true);
        return properties;
    }
}
