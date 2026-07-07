package com.weedrice.whiteboard.domain.auth.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetConfirmRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("UUID 형식의 비밀번호 재설정 token은 허용한다")
    void validate_uuidToken_success() {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest(
                "123e4567-e89b-12d3-a456-426614174000",
                "Password123!");

        var violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("짧은 비밀번호 재설정 token은 거부한다")
    void validate_shortToken_reportsTokenViolation() {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("short", "Password123!");

        var violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("token"));
    }

    @Test
    @DisplayName("UUID 형식이 아닌 비밀번호 재설정 token은 거부한다")
    void validate_invalidTokenFormat_reportsTokenViolation() {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest(
                "zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz",
                "Password123!");

        var violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("token"));
    }
}
