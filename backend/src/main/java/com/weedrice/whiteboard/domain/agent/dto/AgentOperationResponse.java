package com.weedrice.whiteboard.domain.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;
import java.time.Instant;

public record AgentOperationResponse(
        @JsonProperty("operation_id") String operationId,
        String status,
        String action,
        JsonNode result,
        @JsonProperty("completed_at") Instant completedAt) {
}
