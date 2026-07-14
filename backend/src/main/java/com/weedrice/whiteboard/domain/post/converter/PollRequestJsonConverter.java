package com.weedrice.whiteboard.domain.post.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.post.dto.PollRequest;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PollRequestJsonConverter implements AttributeConverter<PollRequest, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Override
    public String convertToDatabaseColumn(PollRequest attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize poll draft", exception);
        }
    }

    @Override
    public PollRequest convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, PollRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to deserialize poll draft", exception);
        }
    }
}
