package com.weedrice.whiteboard.domain.notification.constant;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;

import java.util.Locale;
import java.util.Set;

public final class NotificationType {
    public static final String LIKE = "LIKE";
    public static final String COMMENT = "COMMENT";
    public static final String REPLY = "REPLY";

    private static final Set<String> SUPPORTED_TYPES = Set.of(LIKE, COMMENT, REPLY);

    private NotificationType() {
    }

    public static String normalize(String value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized;
    }

    public static boolean isSupported(String value) {
        if (value == null) {
            return false;
        }
        return SUPPORTED_TYPES.contains(value.trim().toUpperCase(Locale.ROOT));
    }
}
