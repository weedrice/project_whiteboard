package com.weedrice.whiteboard.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 비밀번호 강도 검증 어노테이션
 * 
 * 비밀번호가 다음 조건을 만족하는지 검증합니다:
 * - 최소 8자 이상
 * - 영문 대문자, 소문자, 숫자, 특수문자 중 최소 3종류 포함
 */
@Documented
@Constraint(validatedBy = PasswordStrengthValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordStrength {
    String message() default "비밀번호는 영문 대소문자, 숫자, 특수문자 중 최소 3종류를 포함하여 8자 이상이어야 합니다";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
