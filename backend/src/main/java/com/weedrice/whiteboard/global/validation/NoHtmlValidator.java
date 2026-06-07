package com.weedrice.whiteboard.global.validation;

import com.weedrice.whiteboard.global.util.HtmlSafetyUtils;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NoHtmlValidator implements ConstraintValidator<NoHtml, String> {

    @Override
    public void initialize(NoHtml constraintAnnotation) {
        // No initialization required.
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }

        if (HtmlSafetyUtils.containsScriptTag(value)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("스크립트 태그는 허용되지 않습니다").addConstraintViolation();
            return false;
        }

        if (HtmlSafetyUtils.containsEventHandler(value)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("이벤트 핸들러는 허용되지 않습니다").addConstraintViolation();
            return false;
        }

        return !HtmlSafetyUtils.containsHtmlTag(value);
    }

    public static boolean containsUnsafeHtml(String value) {
        return HtmlSafetyUtils.containsUnsafeHtml(value);
    }
}
