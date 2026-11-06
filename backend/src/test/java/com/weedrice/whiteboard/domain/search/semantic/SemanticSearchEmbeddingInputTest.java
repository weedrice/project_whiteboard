package com.weedrice.whiteboard.domain.search.semantic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticSearchEmbeddingInputTest {
    @ParameterizedTest
    @ValueSource(strings = {"한글", "日本語", "😀🧑‍💻", "a", "é"})
    void limitsUtf8BytesWithoutSplittingCodePoints(String unit) {
        String input = unit.repeat(9_000);
        String result = SemanticSearchEmbeddingInput.limit(input);

        assertThat(result.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(8_000);
        assertThat(input).startsWith(result);
        assertThat(new String(result.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)).isEqualTo(result);
        assertThat(Character.isHighSurrogate(result.charAt(result.length() - 1))).isFalse();
        assertThat(SemanticSearchEmbeddingInput.limit(result)).isEqualTo(result);
        int next = input.codePointAt(result.length());
        assertThat((result + new String(Character.toChars(next))).getBytes(StandardCharsets.UTF_8).length)
                .isGreaterThan(8_000);
    }

    @Test
    void preservesShortTextAndExactBoundary() {
        assertThat(SemanticSearchEmbeddingInput.limit("가나다 😀 日本語")).isEqualTo("가나다 😀 日本語");
        assertThat(SemanticSearchEmbeddingInput.limit("x".repeat(8_000))).hasSize(8_000);
        assertThat(SemanticSearchEmbeddingInput.limit("x".repeat(7_999) + "😀")).hasSize(7_999);
        assertThat(SemanticSearchEmbeddingInput.limit(null)).isEmpty();
    }
}
