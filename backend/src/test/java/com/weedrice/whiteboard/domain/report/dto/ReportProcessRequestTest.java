package com.weedrice.whiteboard.domain.report.dto;

import com.weedrice.whiteboard.domain.report.entity.ReportStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ReportProcessRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("status가 없으면 NotNull 위반만 발생한다")
    void validate_missingStatus_reportsSingleViolation() {
        ReportProcessRequest request = new ReportProcessRequest();

        var violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("status");
    }

    @Test
    @DisplayName("PENDING status는 AssertTrue 검증으로 거부된다")
    void validate_pendingStatus_reportsTerminalViolation() {
        ReportProcessRequest request = new ReportProcessRequest();
        ReflectionTestUtils.setField(request, "status", ReportStatus.PENDING);

        var violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("terminalStatusValid");
    }
}
