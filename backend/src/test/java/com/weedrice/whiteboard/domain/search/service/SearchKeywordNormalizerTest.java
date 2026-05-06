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
}
