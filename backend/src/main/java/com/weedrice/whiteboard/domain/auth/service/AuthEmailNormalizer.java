package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;

final class AuthEmailNormalizer {

    static final int MAX_EMAIL_LENGTH = 100;

    private AuthEmailNormalizer() {
    }

    static String normalize(String email) {
        if (email == null || email.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "email is required");
        }
        String normalizedEmail = email.trim();
        if (normalizedEmail.length() > MAX_EMAIL_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "email must be 100 characters or less");
        }
        return normalizedEmail;
    }
}
