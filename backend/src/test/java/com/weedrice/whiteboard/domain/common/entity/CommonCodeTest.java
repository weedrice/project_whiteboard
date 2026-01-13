package com.weedrice.whiteboard.domain.common.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommonCodeTest {

    @Test
    @DisplayName("CommonCode 생성 성공")
    void createCommonCode_success() {
        // when
        CommonCode commonCode = CommonCode.builder()
                .typeCode("TEST_TYPE")
                .typeName("Test Type")
                .description("Test Description")
                .build();

        // then
        assertThat(commonCode.getTypeCode()).isEqualTo("TEST_TYPE");
        assertThat(commonCode.getTypeName()).isEqualTo("Test Type");
        assertThat(commonCode.getDescription()).isEqualTo("Test Description");
    }

    @Test
    @DisplayName("CommonCode 수정 성공")
    void updateCommonCode_success() {
        // given
        CommonCode commonCode = CommonCode.builder()
                .typeCode("TEST_TYPE")
                .typeName("Original Type")
                .description("Original Description")
                .build();

        // when
        commonCode.update("Updated Type", "Updated Description");

        // then
        assertThat(commonCode.getTypeName()).isEqualTo("Updated Type");
        assertThat(commonCode.getDescription()).isEqualTo("Updated Description");
    }
}
