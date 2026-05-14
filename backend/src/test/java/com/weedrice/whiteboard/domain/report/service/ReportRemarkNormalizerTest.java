package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.report.constant.ReportConstraints;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportRemarkNormalizerTest {

    @Test
    @DisplayName("normalize returns null when remark is null")
    void normalize_null_returnsNull() {
        assertThat(ReportRemarkNormalizer.normalize(null)).isNull();
    }

    @Test
    @DisplayName("normalize strips surrounding whitespace")
    void normalize_stripsWhitespace() {
        assertThat(ReportRemarkNormalizer.normalize("  done  ")).isEqualTo("done");
    }

    @Test
    @DisplayName("normalize keeps blank remark as empty string")
    void normalize_blank_returnsEmptyString() {
        assertThat(ReportRemarkNormalizer.normalize("   ")).isEmpty();
    }

    @Test
    @DisplayName("normalize accepts max length remark after strip")
    void normalize_maxLengthRemark_success() {
        String remark = " " + "a".repeat(ReportConstraints.MAX_REMARK_LENGTH) + " ";

        assertThat(ReportRemarkNormalizer.normalize(remark))
                .hasSize(ReportConstraints.MAX_REMARK_LENGTH);
    }

    @Test
    @DisplayName("normalize rejects overlong remark")
    void normalize_overlongRemark_throwsValidationError() {
        assertThatThrownBy(() -> ReportRemarkNormalizer.normalize(
                "a".repeat(ReportConstraints.MAX_REMARK_LENGTH + 1)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
    }
}
