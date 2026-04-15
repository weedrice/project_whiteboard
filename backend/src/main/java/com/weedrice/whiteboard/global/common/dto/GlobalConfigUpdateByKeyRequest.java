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

    @NotBlank(message = "설정 값은 필수입니다.")
    @Size(max = 255, message = "설정 값은 255자 이하여야 합니다.")
    private String value;

    @Size(max = 255, message = "설명은 255자 이하여야 합니다.")
    private String description;
}
