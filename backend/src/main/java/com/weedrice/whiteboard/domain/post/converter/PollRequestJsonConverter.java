package com.weedrice.whiteboard.domain.post.converter;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.weedrice.whiteboard.domain.post.dto.PollRequest;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PollRequestJsonConverter implements AttributeConverter<PollRequest, String> {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Override
    public String convertToDatabaseColumn(PollRequest attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JacksonException exception) {
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
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Failed to deserialize poll draft", exception);
        }
    }
}
