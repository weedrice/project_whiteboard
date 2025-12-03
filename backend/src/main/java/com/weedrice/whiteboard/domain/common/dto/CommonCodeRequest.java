package com.weedrice.whiteboard.domain.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CommonCodeRequest {
    @NotBlank
    private String typeCode;
    @NotBlank
    private String typeName;
    private String description;
}
