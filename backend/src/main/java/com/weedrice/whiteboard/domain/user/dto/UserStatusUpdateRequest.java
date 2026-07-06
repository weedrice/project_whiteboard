package com.weedrice.whiteboard.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserStatusUpdateRequest {
    @NotBlank
    @Pattern(regexp = "ACTIVE|SUSPENDED")
    private String status; // ACTIVE, SUSPENDED
}
