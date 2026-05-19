package com.weedrice.whiteboard.domain.board.service;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

class BoardCategoryConstraintResolverTest {

    @Test
    @DisplayName("활성 카테고리 중복 제약명을 메시지에서 판별한다")
    void isActiveNameConstraint_detectsConstraintNameFromMessage() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "duplicate key value violates constraint uq_board_categories_active_name");

        assertThat(BoardCategoryConstraintResolver.isActiveNameConstraint(exception)).isTrue();
    }

    @Test
    @DisplayName("활성 카테고리 중복 제약명을 Hibernate 원인에서 판별한다")
    void isActiveNameConstraint_detectsConstraintNameFromCause() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "duplicate",
                new ConstraintViolationException(
                        "duplicate",
                        null,
                        "uk_board_categories_board_name_active"));

        assertThat(BoardCategoryConstraintResolver.isActiveNameConstraint(exception)).isTrue();
    }

    @Test
    @DisplayName("활성 카테고리 중복 제약이 아니면 false를 반환한다")
    void isActiveNameConstraint_returnsFalseForOtherConstraint() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "duplicate key value violates constraint uk_boards_board_name");

        assertThat(BoardCategoryConstraintResolver.isActiveNameConstraint(exception)).isFalse();
    }
}
