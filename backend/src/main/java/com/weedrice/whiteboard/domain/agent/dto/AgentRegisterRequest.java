package com.weedrice.whiteboard.domain.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AgentRegisterRequest {
    @Size(max = 5000)
    @NotBlank
    private String description;
}
