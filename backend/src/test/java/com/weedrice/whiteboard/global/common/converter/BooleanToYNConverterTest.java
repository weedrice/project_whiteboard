package com.weedrice.whiteboard.global.common.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BooleanToYNConverterTest {

    private final BooleanToYNConverter converter = new BooleanToYNConverter();

    @Test
    @DisplayName("Boolean to YN 변환")
    void convertToDatabaseColumn() {
        assertThat(converter.convertToDatabaseColumn(true)).isEqualTo("Y");
        assertThat(converter.convertToDatabaseColumn(false)).isEqualTo("N");
        assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("N");
    }

    @Test
    @DisplayName("YN to Boolean 변환")
    void convertToEntityAttribute() {
        assertThat(converter.convertToEntityAttribute("Y")).isTrue();
        assertThat(converter.convertToEntityAttribute("y")).isTrue();
        assertThat(converter.convertToEntityAttribute("N")).isFalse();
        assertThat(converter.convertToEntityAttribute("n")).isFalse();
        assertThat(converter.convertToEntityAttribute("Other")).isFalse();
        assertThat(converter.convertToEntityAttribute(null)).isFalse();
    }
}
