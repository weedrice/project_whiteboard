package com.weedrice.whiteboard.global.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GlobalConfigUpdateByKeyRequest {

    @NotBlank(message = "{validation.config.value.required}")
    @Size(max = 10000, message = "{validation.config.value.max}")
    private String value;

    @Size(max = 255, message = "{validation.config.description.max}")
    private String description;
}
