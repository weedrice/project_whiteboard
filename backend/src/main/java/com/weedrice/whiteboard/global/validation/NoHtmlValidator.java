package com.weedrice.whiteboard.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * HTML 태그 검증 Validator
 * 
 * XSS 공격 방지를 위해 HTML 태그가 포함되어 있는지 검증합니다.
 */
public class NoHtmlValidator implements ConstraintValidator<NoHtml, String> {

    // HTML 태그를 감지하는 정규식
    // <로 시작하고 >로 끝나는 태그 패턴
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile(
            "<[^>]+>",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    // 위험한 스크립트 태그 패턴
    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
            "<\\s*script[^>]*>.*?</\\s*script\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL
    );

    // 위험한 이벤트 핸들러 패턴 (onclick, onerror 등)
    private static final Pattern EVENT_HANDLER_PATTERN = Pattern.compile(
            "on\\w+\\s*=",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public void initialize(NoHtml constraintAnnotation) {
        // 초기화 로직이 필요하면 여기에 추가
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // null/blank는 다른 검증에서 처리
        }

        // 스크립트 태그 검사
        if (SCRIPT_PATTERN.matcher(value).find()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("스크립트 태그는 허용되지 않습니다").addConstraintViolation();
            return false;
        }

        // 이벤트 핸들러 검사
        if (EVENT_HANDLER_PATTERN.matcher(value).find()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("이벤트 핸들러는 허용되지 않습니다").addConstraintViolation();
            return false;
        }

        // HTML 태그 검사
        if (HTML_TAG_PATTERN.matcher(value).find()) {
            return false;
        }

        return true;
    }
}
