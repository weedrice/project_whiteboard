package com.weedrice.whiteboard.domain.notification.constant;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;

import java.util.Locale;

public enum NotificationType {
    LIKE,
    COMMENT,
    REPLY;

    public static NotificationType normalize(String value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        try {
            return NotificationType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    public static NotificationType fromDatabaseValue(String value) {
        if (value == null) {
            return null;
        }

        try {
            return NotificationType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported notification type in database: " + value, exception);
        }
    }

    public static boolean isSupported(String value) {
        if (value == null) {
            return false;
        }

        try {
            NotificationType.valueOf(value.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
