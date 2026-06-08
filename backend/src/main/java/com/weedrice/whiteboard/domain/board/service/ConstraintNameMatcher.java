package com.weedrice.whiteboard.domain.board.service;

import org.hibernate.exception.ConstraintViolationException;

import java.util.Locale;

final class ConstraintNameMatcher {

    private static final String BOARD_NAME_CONSTRAINT = "uk_boards_board_name";
    private static final String BOARD_URL_CONSTRAINT = "uk_boards_board_url";
    private static final String LEGACY_BOARD_NAME_CONSTRAINT = "boards_board_name_key";
    private static final String LEGACY_BOARD_URL_CONSTRAINT = "boards_board_url_key";
    private static final String BOARD_NAME_COLUMN = "board_name";
    private static final String BOARD_URL_COLUMN = "board_url";

    private ConstraintNameMatcher() {
    }

    static boolean containsBoardNameConstraint(Throwable throwable) {
        return containsConstraint(
                throwable,
                BOARD_NAME_CONSTRAINT,
                LEGACY_BOARD_NAME_CONSTRAINT,
                BOARD_NAME_COLUMN);
    }

    static boolean containsBoardUrlConstraint(Throwable throwable) {
        return containsConstraint(
                throwable,
                BOARD_URL_CONSTRAINT,
                LEGACY_BOARD_URL_CONSTRAINT,
                BOARD_URL_COLUMN);
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
