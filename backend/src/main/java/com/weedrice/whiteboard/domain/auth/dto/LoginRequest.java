package com.weedrice.whiteboard.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    @NotBlank
    @Size(min = 4, max = 30)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "{validation.loginId.pattern}")
    private String loginId;

    @NotBlank
    @Size(min = 8, max = 20)
    private String password;
}
