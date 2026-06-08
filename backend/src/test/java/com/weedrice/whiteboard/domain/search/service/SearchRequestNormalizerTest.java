package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchRequestNormalizerTest {

    @Test
    void canonicalizeKeyword_rejectsMissingRequiredKeyword() {
        assertInvalidRequiredKeyword(null);
        assertInvalidRequiredKeyword(" ");
    }

    @Test
    void canonicalizeOptionalKeyword_returnsNullForMissingKeyword() {
        assertThat(SearchRequestNormalizer.canonicalizeOptionalKeyword(null)).isNull();
        assertThat(SearchRequestNormalizer.canonicalizeOptionalKeyword(" ")).isNull();
    }

    @Test
    void canonicalizeKeyword_trimsAndTruncatesKeyword() {
        String keyword = " " + "A".repeat(SearchRequestNormalizer.MAX_KEYWORD_LENGTH + 1) + " ";

        assertThat(SearchRequestNormalizer.canonicalizeKeyword(keyword))
                .isEqualTo("A".repeat(SearchRequestNormalizer.MAX_KEYWORD_LENGTH));
        assertThat(SearchRequestNormalizer.canonicalizeOptionalKeyword(keyword))
                .isEqualTo("A".repeat(SearchRequestNormalizer.MAX_KEYWORD_LENGTH));
    }

    private void assertInvalidRequiredKeyword(String keyword) {
        assertThatThrownBy(() -> SearchRequestNormalizer.canonicalizeKeyword(keyword))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }
}
