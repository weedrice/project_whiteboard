package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;

import java.util.Locale;

public final class AuthEmailNormalizer {

    static final int MAX_EMAIL_LENGTH = 100;

    private AuthEmailNormalizer() {
    }

    public static String normalize(String email) {
        if (email == null || email.isBlank()) {
            throw BusinessException.withMessageKey(ErrorCode.VALIDATION_ERROR, "validation.email.required");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.length() > MAX_EMAIL_LENGTH) {
            throw BusinessException.withMessageKey(ErrorCode.VALIDATION_ERROR, "validation.email.max");
        }
        return normalizedEmail;
    }
}
