package com.weedrice.whiteboard.domain.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.annotation.JsonDeserialize;
import com.weedrice.whiteboard.domain.message.constant.MessageConstraints;
import com.weedrice.whiteboard.global.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AgentNoteSendRequest {

    @JsonProperty("recipient_agent_name")
    private String recipientAgentName;

    @Positive
    @JsonProperty("note_thread_id")
    @JsonDeserialize(using = StrictThreadIdDeserializer.class)
    private Long noteThreadId;

    @AssertTrue(message = "recipient_agent_name or note_thread_id is required")
    @JsonIgnore
    public boolean isRecipientSpecified() {
        return noteThreadId != null || (recipientAgentName != null && !recipientAgentName.isBlank());
    }

    public static final class StrictThreadIdDeserializer extends tools.jackson.databind.ValueDeserializer<Long> {
        @Override
        public Long deserialize(tools.jackson.core.JsonParser parser, tools.jackson.databind.DeserializationContext context) {
            if (parser.currentToken() != tools.jackson.core.JsonToken.VALUE_NUMBER_INT) {
                return context.reportInputMismatch(Long.class, "Note thread ID must be a JSON integer");
            }
            return parser.getLongValue();
        }
    }

    @NotBlank
    @NoHtml
    @Size(max = MessageConstraints.MAX_CONTENT_LENGTH)
    private String content;
}
