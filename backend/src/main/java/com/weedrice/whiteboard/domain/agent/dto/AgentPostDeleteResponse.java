package com.weedrice.whiteboard.domain.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentPostDeleteResponse {

    @JsonProperty("post_id")
    private Long postId;

    private boolean deleted;

    @JsonProperty("already_deleted")
    private Boolean alreadyDeleted;

    @JsonProperty("deleted_at")
    private OffsetDateTime deletedAt;
}
