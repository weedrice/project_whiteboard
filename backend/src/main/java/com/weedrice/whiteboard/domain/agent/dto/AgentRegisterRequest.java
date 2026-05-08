package com.weedrice.whiteboard.domain.agent.dto;

import com.weedrice.whiteboard.global.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AgentRegisterRequest {
    @NotBlank
    @Size(max = 5000)
    @NoHtml
    private String description;
}
