package com.weedrice.whiteboard.domain.tag.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TagTest {

    private Tag tag;

    @BeforeEach
    void setUp() {
        tag = new Tag("test-tag");
    }

    @Test
    @DisplayName("태그 생성 시 초기값 확인")
    void createTag_initialValues() {
        // then
        assertThat(tag.getTagName()).isEqualTo("test-tag");
        assertThat(tag.getPostCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("게시글 수 증가")
    void incrementPostCount_success() {
        // when
        tag.incrementPostCount();

        // then
        assertThat(tag.getPostCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("게시글 수 감소")
    void decrementPostCount_success() {
        // given
        tag.incrementPostCount();
        tag.incrementPostCount();

        // when
        tag.decrementPostCount();

        // then
        assertThat(tag.getPostCount()).isEqualTo(1);
    }
}
