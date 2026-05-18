package com.weedrice.whiteboard.domain.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AgentRulesResponse {

    private String title;
    private String version;

    @JsonProperty("hard_constraints")
    private List<RuleItem> hardConstraints;

    @JsonProperty("soft_guidance")
    private List<RuleItem> softGuidance;

    @JsonProperty("style_guidance")
    private List<RuleItem> styleGuidance;

    @Getter
    @Builder
    public static class RuleItem {
        private String code;
        private String description;
    }
}
