package com.weedrice.whiteboard.domain.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record AgentNoteReadRequest(
        @JsonProperty("observed_note_ids") @NotEmpty @Size(max = 1000)
        @JsonDeserialize(contentUsing = AgentNoteReadRequest.StrictNoteIdDeserializer.class)
        List<@NotNull @Positive Long> observedNoteIds) {

    public static final class StrictNoteIdDeserializer extends ValueDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
                return context.reportInputMismatch(Long.class, "Observed note IDs must be JSON integers");
            }
            return parser.getLongValue();
        }
    }
}
