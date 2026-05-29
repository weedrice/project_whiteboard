package com.weedrice.whiteboard.global.common.util;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;

public final class TextInputNormalizer {

    private TextInputNormalizer() {
    }

    public static String normalizeRequired(String value, int maxLength) {
        String normalizedValue = normalizeNullable(value);
        if (normalizedValue == null || normalizedValue.isBlank() || normalizedValue.length() > maxLength) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedValue;
    }

    public static String normalizeOptional(String value, int maxLength) {
        String normalizedValue = normalizeNullable(value);
        if (normalizedValue != null && normalizedValue.length() > maxLength) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedValue;
    }

    public static String normalizeNullable(String value) {
        return value == null ? null : value.trim();
    }
}
