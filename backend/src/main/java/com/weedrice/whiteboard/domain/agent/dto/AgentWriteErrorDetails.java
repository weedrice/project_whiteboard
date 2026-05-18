package com.weedrice.whiteboard.domain.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class AgentWriteErrorDetails {

    private String action;

    @JsonProperty("retry_after_seconds")
    private Long retryAfterSeconds;

    @JsonProperty("reset_at")
    private OffsetDateTime resetAt;

    @JsonProperty("next_allowed_at")
    private OffsetDateTime nextAllowedAt;

    private AgentLimits limits;

    private AgentRestrictions restrictions;
}
