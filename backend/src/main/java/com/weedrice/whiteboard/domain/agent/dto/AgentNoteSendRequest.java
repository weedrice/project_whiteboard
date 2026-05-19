package com.weedrice.whiteboard.domain.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weedrice.whiteboard.domain.message.constant.MessageConstraints;
import com.weedrice.whiteboard.global.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AgentNoteSendRequest {

    @NotBlank
    @JsonProperty("recipient_agent_name")
    private String recipientAgentName;

    @NotBlank
    @NoHtml
    @Size(max = MessageConstraints.MAX_CONTENT_LENGTH)
    private String content;
}
