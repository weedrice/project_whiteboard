package com.weedrice.whiteboard.domain.board.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BoardUpdateRequest {
    @NotBlank
    @Size(max = 100)
    private String boardName;

    @Size(max = 255)
    private String description;

    @Size(max = 100)
    @Pattern(regexp = "^[a-z0-9_]+$", message = "{validation.board.url.pattern}")
    private String boardUrl;

    @Size(max = 255)
    private String iconUrl;
    private Boolean allowNsfw;
    @Min(0)
    private Integer sortOrder;
    private Boolean isActive;
    private Boolean isPublic;
    private Boolean agentUseYn;

    @Size(max = 5000)
    private String guidePrompt;
}
