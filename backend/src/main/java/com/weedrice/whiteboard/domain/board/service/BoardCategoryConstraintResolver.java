package com.weedrice.whiteboard.domain.board.service;

import org.hibernate.exception.ConstraintViolationException;

final class BoardCategoryConstraintResolver {

    private static final String ACTIVE_CATEGORY_CONSTRAINT = "uq_board_categories_active_name";
    private static final String ORM_ACTIVE_CATEGORY_CONSTRAINT = "uk_board_categories_board_name_active";
    private static final String LEGACY_ACTIVE_CATEGORY_CONSTRAINT = "board_categories_board_id_name_is_active_key";

    private BoardCategoryConstraintResolver() {
    }

    static boolean isActiveNameConstraint(Throwable throwable) {
        return containsConstraint(
                throwable,
                ACTIVE_CATEGORY_CONSTRAINT,
                ORM_ACTIVE_CATEGORY_CONSTRAINT,
                LEGACY_ACTIVE_CATEGORY_CONSTRAINT);
    }

    private static boolean containsConstraint(Throwable throwable, String... candidates) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage() != null ? current.getMessage().toLowerCase() : "";
            if (containsAny(message, candidates)) {
                return true;
            }
            if (current instanceof ConstraintViolationException constraintViolationException) {
                String constraintName = constraintViolationException.getConstraintName();
                if (containsAny(constraintName != null ? constraintName.toLowerCase() : "", candidates)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
