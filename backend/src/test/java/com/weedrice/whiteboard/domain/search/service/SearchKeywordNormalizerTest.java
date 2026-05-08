package com.weedrice.whiteboard.domain.search.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchKeywordNormalizerTest {

    @Test
    @DisplayName("정규화 키워드는 trim과 Locale.ROOT 소문자 규칙을 따른다")
    void normalize_trimsAndLowercases() {
        assertThat(SearchKeywordNormalizer.normalize("\t Test KEYWORD \n"))
                .isEqualTo("test keyword");
    }

    @Test
    @DisplayName("normalized search keyword is capped at 255 characters")
    void normalize_truncatesToMaxKeywordLengthAfterLowercase() {
        String keyword = "A".repeat(SearchRequestNormalizer.MAX_KEYWORD_LENGTH + 10);

        String normalizedKeyword = SearchKeywordNormalizer.normalize(keyword);

        assertThat(normalizedKeyword)
                .hasSize(SearchRequestNormalizer.MAX_KEYWORD_LENGTH)
                .isEqualTo("a".repeat(SearchRequestNormalizer.MAX_KEYWORD_LENGTH));
    }

    @Test
    @DisplayName("keyword truncation does not split surrogate pairs")
    void normalize_doesNotSplitSurrogatePair() {
        String keyword = "A".repeat(SearchRequestNormalizer.MAX_KEYWORD_LENGTH - 1) + "\uD83D\uDE00";

        String normalizedKeyword = SearchKeywordNormalizer.normalize(keyword);

        assertThat(normalizedKeyword)
                .hasSize(SearchRequestNormalizer.MAX_KEYWORD_LENGTH - 1)
                .isEqualTo("a".repeat(SearchRequestNormalizer.MAX_KEYWORD_LENGTH - 1));
    }
}
