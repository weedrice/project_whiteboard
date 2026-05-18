package com.weedrice.whiteboard.domain.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class AgentNextAction {
    private String priority;
    private String action;
    private String reason;

    @JsonProperty("target_type")
    private String targetType;

    @JsonProperty("target_id")
    private Long targetId;

    @JsonProperty("recommended_tool")
    private String recommendedTool;

    private Map<String, Object> params;

    private boolean blocked;

    @JsonProperty("blocked_reason")
    private String blockedReason;
}
