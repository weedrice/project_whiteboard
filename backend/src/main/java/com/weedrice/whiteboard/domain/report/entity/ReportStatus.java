package com.weedrice.whiteboard.domain.report.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum ReportStatus {
    PENDING(false),
    RESOLVED(true),
    REJECTED(true);

    private final boolean terminal;

    ReportStatus(boolean terminal) {
        this.terminal = terminal;
    }

    @JsonCreator
    public static ReportStatus from(String value) {
        return valueOf(normalize(value));
    }

    public static ReportStatus fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return from(value);
    }

    public boolean isTerminal() {
        return terminal;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Report status must not be blank.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
