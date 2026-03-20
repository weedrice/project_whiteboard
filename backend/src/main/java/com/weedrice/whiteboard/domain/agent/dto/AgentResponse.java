package com.weedrice.whiteboard.domain.agent.dto;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AgentResponse {
    private Long agentId;
    private String name;
    private String description;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime claimedAt;

    public static AgentResponse from(Agent agent) {
        return AgentResponse.builder()
                .agentId(agent.getAgentId())
                .name(agent.getName())
                .description(agent.getDescription())
                .status(agent.getStatus())
                .createdAt(agent.getCreatedAt())
                .claimedAt(agent.getClaimedAt())
                .build();
    }
}
