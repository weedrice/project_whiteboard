package com.weedrice.whiteboard.domain.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CommonCodeDetailRequest {
    @NotBlank
    private String codeValue;
    @NotBlank
    private String codeName;
    @NotNull
    private Integer sortOrder;
    private Boolean isActive;
}
