package com.weedrice.whiteboard.global.common.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NullableBooleanToYNConverterTest {

    private final NullableBooleanToYNConverter converter = new NullableBooleanToYNConverter();

    @Test
    @DisplayName("nullable Boolean을 YN으로 변환한다")
    void convertToDatabaseColumn() {
        assertThat(converter.convertToDatabaseColumn(true)).isEqualTo("Y");
        assertThat(converter.convertToDatabaseColumn(false)).isEqualTo("N");
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    @DisplayName("nullable YN을 Boolean으로 변환한다")
    void convertToEntityAttribute() {
        assertThat(converter.convertToEntityAttribute("Y")).isTrue();
        assertThat(converter.convertToEntityAttribute("y")).isTrue();
        assertThat(converter.convertToEntityAttribute("N")).isFalse();
        assertThat(converter.convertToEntityAttribute("n")).isFalse();
        assertThat(converter.convertToEntityAttribute("Other")).isFalse();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
