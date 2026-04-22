package com.weedrice.whiteboard.domain.report.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum ReportTargetType {
    POST,
    COMMENT,
    USER;

    @JsonCreator
    public static ReportTargetType from(String value) {
        return valueOf(normalize(value));
    }

    public static ReportTargetType fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return from(value);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Report target type must not be blank.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
