package com.weedrice.whiteboard.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SuperAdminRequest {
    @NotBlank
    private String loginId;
    @NotBlank
    @Size(max = 500)
    private String reason;
}
