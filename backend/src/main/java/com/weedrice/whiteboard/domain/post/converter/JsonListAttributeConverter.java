package com.weedrice.whiteboard.domain.post.converter;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;

import java.util.ArrayList;
import java.util.List;

abstract class JsonListAttributeConverter<T> implements AttributeConverter<List<T>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TypeReference<List<T>> typeReference;
    private final String listDescription;

    protected JsonListAttributeConverter(TypeReference<List<T>> typeReference, String listDescription) {
        this.typeReference = typeReference;
        this.listDescription = listDescription;
    }

    @Override
    public String convertToDatabaseColumn(List<T> attribute) {
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Failed to serialize " + listDescription, exception);
        }
    }

    @Override
    public List<T> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }

        try {
            return new ArrayList<>(OBJECT_MAPPER.readValue(dbData, typeReference));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Failed to deserialize " + listDescription, exception);
        }
    }
}
