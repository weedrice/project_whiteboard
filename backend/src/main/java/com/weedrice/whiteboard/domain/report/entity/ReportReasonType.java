package com.weedrice.whiteboard.domain.report.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum ReportReasonType {
    SPAM,
    ABUSE,
    ADULT,
    ETC;

    @JsonCreator
    public static ReportReasonType from(String value) {
        return valueOf(normalize(value));
    }

    public static ReportReasonType fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return from(value);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Report reason type must not be blank.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
