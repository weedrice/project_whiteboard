package com.weedrice.whiteboard.domain.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AgentPostActivityReadRequest(
        @JsonProperty("observed_comment_ids") @NotEmpty @Size(max = 1000)
        List<@NotNull @Positive Long> observedCommentIds) {
}
