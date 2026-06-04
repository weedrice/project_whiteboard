package com.weedrice.whiteboard.domain.board.service;

import org.hibernate.exception.ConstraintViolationException;

import java.util.Locale;

final class ConstraintNameMatcher {

    private ConstraintNameMatcher() {
    }

    static boolean containsConstraint(Throwable throwable, String... candidates) {
        Throwable current = throwable;
        while (current != null) {
            if (containsAny(normalize(current.getMessage()), candidates)) {
                return true;
            }
            if (current instanceof ConstraintViolationException constraintViolationException
                    && containsAny(normalize(constraintViolationException.getConstraintName()), candidates)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && value.contains(normalize(candidate))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
