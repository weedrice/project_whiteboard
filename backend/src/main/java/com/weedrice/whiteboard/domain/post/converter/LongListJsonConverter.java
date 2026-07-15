package com.weedrice.whiteboard.domain.post.converter;

import tools.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class LongListJsonConverter extends JsonListAttributeConverter<Long> {

    private static final TypeReference<List<Long>> TYPE_REFERENCE = new TypeReference<>() {
    };

    public LongListJsonConverter() {
        super(TYPE_REFERENCE, "long list");
    }
}
