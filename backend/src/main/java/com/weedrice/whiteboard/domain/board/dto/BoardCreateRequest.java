package com.weedrice.whiteboard.domain.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
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

    private String iconUrl;
}
