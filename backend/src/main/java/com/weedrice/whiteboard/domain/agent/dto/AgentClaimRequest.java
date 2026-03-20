package com.weedrice.whiteboard.domain.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AgentClaimRequest {
    @NotBlank
    private String agentToken;
}
