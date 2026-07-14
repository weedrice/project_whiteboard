package com.weedrice.whiteboard.domain.notification.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;

public final class NotificationMessageParamsCodec {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() { };

    private NotificationMessageParamsCodec() { }

    public static String encode(List<String> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(params);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Notification message parameters could not be encoded", exception);
        }
    }

    public static List<String> decode(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> params = OBJECT_MAPPER.readValue(paramsJson, STRING_LIST_TYPE);
            return params == null
                    ? List.of()
                    : params.stream().filter(Objects::nonNull).toList();
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
