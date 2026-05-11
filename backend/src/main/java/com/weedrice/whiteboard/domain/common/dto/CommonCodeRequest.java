package com.weedrice.whiteboard.domain.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CommonCodeRequest {
    @NotBlank
    @Size(max = 50)
    private String typeCode;
    @NotBlank
    @Size(max = 100)
    private String typeName;
    @Size(max = 255)
    private String description;

    public void setDescription(String description) {
        this.description = description == null ? null : description.trim();
    }
}
