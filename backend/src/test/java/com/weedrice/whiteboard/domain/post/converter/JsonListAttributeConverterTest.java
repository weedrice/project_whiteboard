package com.weedrice.whiteboard.domain.post.converter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonListAttributeConverterTest {

    private final StringListJsonConverter stringConverter = new StringListJsonConverter();
    private final LongListJsonConverter longConverter = new LongListJsonConverter();

    @Test
    void convertsNullAttributesToEmptyJsonArrays() {
        assertThat(stringConverter.convertToDatabaseColumn(null)).isEqualTo("[]");
        assertThat(longConverter.convertToDatabaseColumn(null)).isEqualTo("[]");
    }

    @Test
    void convertsJsonArraysToMutableLists() {
        List<String> strings = stringConverter.convertToEntityAttribute("[\"a\",\"b\"]");
        List<Long> longs = longConverter.convertToEntityAttribute("[1,2]");

        strings.add("c");
        longs.add(3L);

        assertThat(strings).containsExactly("a", "b", "c");
        assertThat(longs).containsExactly(1L, 2L, 3L);
    }

    @Test
    void convertsBlankDatabaseValuesToEmptyLists() {
        assertThat(stringConverter.convertToEntityAttribute(null)).isEmpty();
        assertThat(stringConverter.convertToEntityAttribute(" ")).isEmpty();
        assertThat(longConverter.convertToEntityAttribute(null)).isEmpty();
        assertThat(longConverter.convertToEntityAttribute(" ")).isEmpty();
    }

    @Test
    void reportsConverterSpecificDeserializeErrors() {
        assertThatThrownBy(() -> stringConverter.convertToEntityAttribute("{"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to deserialize string list");

        assertThatThrownBy(() -> longConverter.convertToEntityAttribute("{"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to deserialize long list");
    }
}
