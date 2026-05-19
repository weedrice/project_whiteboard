package com.weedrice.whiteboard.global.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GlobalConfigUpdateRequest {

    @NotBlank(message = "설정 키는 필수입니다.")
    @Size(max = 100, message = "설정 키는 100자 이하여야 합니다.")
    private String key;

    @NotBlank(message = "설정 값은 필수입니다.")
    @Size(max = 10000, message = "설정 값은 10000자 이하여야 합니다.")
    private String value;

    @Size(max = 255, message = "설명은 255자 이하여야 합니다.")
    private String description;
}
