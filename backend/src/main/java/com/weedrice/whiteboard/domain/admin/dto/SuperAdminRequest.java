package com.weedrice.whiteboard.domain.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SuperAdminRequest {
    @NotNull
    private String loginId;
}
