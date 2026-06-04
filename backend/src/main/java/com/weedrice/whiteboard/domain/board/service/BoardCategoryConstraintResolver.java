package com.weedrice.whiteboard.domain.board.service;

final class BoardCategoryConstraintResolver {

    private static final String ACTIVE_CATEGORY_CONSTRAINT = "uq_board_categories_active_name";
    private static final String ORM_ACTIVE_CATEGORY_CONSTRAINT = "uk_board_categories_board_name_active";
    private static final String LEGACY_ACTIVE_CATEGORY_CONSTRAINT = "board_categories_board_id_name_is_active_key";

    private BoardCategoryConstraintResolver() {
    }

    static boolean isActiveNameConstraint(Throwable throwable) {
        return ConstraintNameMatcher.containsConstraint(
                throwable,
                ACTIVE_CATEGORY_CONSTRAINT,
                ORM_ACTIVE_CATEGORY_CONSTRAINT,
                LEGACY_ACTIVE_CATEGORY_CONSTRAINT);
    }
}
