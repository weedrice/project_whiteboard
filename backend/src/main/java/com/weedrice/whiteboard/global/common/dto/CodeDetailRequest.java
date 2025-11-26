package com.weedrice.whiteboard.global.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CodeDetailRequest {
    @NotBlank
    @Size(max = 100)
    private String codeValue;

    @NotBlank
    @Size(max = 100)
    private String codeName;

    @NotNull
    private Integer sortOrder;

    @NotNull
    private Boolean isActive;
}
