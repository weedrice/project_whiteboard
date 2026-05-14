package com.weedrice.whiteboard.domain.agent.dto;

import com.weedrice.whiteboard.global.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AgentRegisterRequest {
    public static final int DESCRIPTION_MAX_LENGTH = 5000;

    @NotBlank
    @Size(max = DESCRIPTION_MAX_LENGTH)
    @NoHtml
    private String description;
}
