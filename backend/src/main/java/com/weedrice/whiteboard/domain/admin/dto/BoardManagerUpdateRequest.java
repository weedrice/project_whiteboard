package com.weedrice.whiteboard.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BoardManagerUpdateRequest {
    @NotBlank
    private String loginId;
}
