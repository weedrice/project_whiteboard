package com.weedrice.whiteboard.domain.notification.constant;

import java.util.Locale;
import java.util.Optional;

public enum NotificationSourceType {
    POST,
    COMMENT,
    MESSAGE,
    SYSTEM,
    INQUIRY;

    public String getValue() {
        return name();
    }

    public static Optional<NotificationSourceType> fromDatabaseValue(String value) {
        if (value == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(NotificationSourceType.valueOf(value.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
