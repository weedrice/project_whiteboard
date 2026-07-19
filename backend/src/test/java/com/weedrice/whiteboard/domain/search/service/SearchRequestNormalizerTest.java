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

    @Test
    void normalizePostSearchType_normalizesSupportedValuesAndKeepsMissingValuesUnset() {
        assertThat(SearchRequestNormalizer.normalizePostSearchType(null)).isNull();
        assertThat(SearchRequestNormalizer.normalizePostSearchType(" ")).isNull();
        assertThat(SearchRequestNormalizer.normalizePostSearchType(" title ")).isEqualTo("TITLE");
    }

    @Test
    void normalizePostSearchType_rejectsUnknownValues() {
        assertThatThrownBy(() -> SearchRequestNormalizer.normalizePostSearchType("TITEL"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    private void assertInvalidRequiredKeyword(String keyword) {
        assertThatThrownBy(() -> SearchRequestNormalizer.canonicalizeKeyword(keyword))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }
}
