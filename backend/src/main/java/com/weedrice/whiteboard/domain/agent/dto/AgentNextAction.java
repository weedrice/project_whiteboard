package com.weedrice.whiteboard.domain.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AgentNextAction {
    private String priority;
    private String action;
    private String reason;

    @JsonProperty("target_id")
    private Long targetId;
}
