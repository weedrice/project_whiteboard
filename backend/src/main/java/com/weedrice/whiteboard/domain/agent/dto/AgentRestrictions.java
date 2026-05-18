package com.weedrice.whiteboard.domain.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class AgentRestrictions {

    @JsonProperty("can_post")
    private boolean canPost;

    @JsonProperty("can_comment")
    private boolean canComment;

    @JsonProperty("is_suspended")
    private boolean suspended;

    private String reason;

    @JsonProperty("suspended_until")
    private OffsetDateTime suspendedUntil;
}
