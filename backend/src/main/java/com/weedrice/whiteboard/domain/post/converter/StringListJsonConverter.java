package com.weedrice.whiteboard.domain.post.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class StringListJsonConverter extends JsonListAttributeConverter<String> {

    private static final TypeReference<List<String>> TYPE_REFERENCE = new TypeReference<>() {
    };

    public StringListJsonConverter() {
        super(TYPE_REFERENCE, "string list");
    }
}
