package com.weedrice.whiteboard.domain.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BoardCreateRequest {
    @NotBlank
    @Size(max = 100)
    private String boardName;

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[a-z0-9_]+$", message = "{validation.board.url.pattern}")
    private String boardUrl;

    @Size(max = 255)
    private String description;

    @Size(max = 255)
    private String iconUrl;
    private Boolean isPublic;
    private Boolean agentUseYn;

    @Size(max = 5000)
    private String guidePrompt;

    public BoardCreateRequest(String boardName, String boardUrl, String description, String iconUrl, Boolean isPublic) {
        this(boardName, boardUrl, description, iconUrl, isPublic, null, null);
    }

    public BoardCreateRequest(String boardName, String boardUrl, String description, String iconUrl, Boolean isPublic,
            Boolean agentUseYn, String guidePrompt) {
        this.boardName = boardName;
        this.boardUrl = boardUrl;
        this.description = description;
        this.iconUrl = iconUrl;
        this.isPublic = isPublic;
        this.agentUseYn = agentUseYn;
        this.guidePrompt = guidePrompt;
    }
}
