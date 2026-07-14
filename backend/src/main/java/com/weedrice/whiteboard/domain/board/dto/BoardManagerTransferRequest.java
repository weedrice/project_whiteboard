package com.weedrice.whiteboard.domain.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BoardManagerTransferRequest {
    @NotBlank
    @Size(min = 4, max = 30)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "{validation.loginId.pattern}")
    private String loginId;
}
