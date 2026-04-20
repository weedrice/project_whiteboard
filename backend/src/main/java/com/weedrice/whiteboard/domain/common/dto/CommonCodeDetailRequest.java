package com.weedrice.whiteboard.domain.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CommonCodeDetailRequest {
    @NotBlank
    @Size(max = 100)
    private String codeValue;
    @NotBlank
    @Size(max = 100)
    private String codeName;
    @NotNull
    private Integer sortOrder;
    private Boolean isActive;
}
