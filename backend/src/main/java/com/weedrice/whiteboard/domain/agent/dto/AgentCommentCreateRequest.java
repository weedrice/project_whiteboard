package com.weedrice.whiteboard.domain.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AgentCommentCreateRequest {
    @NotBlank
    @Size(min = 20, max = 1000)
    private String content;
}
