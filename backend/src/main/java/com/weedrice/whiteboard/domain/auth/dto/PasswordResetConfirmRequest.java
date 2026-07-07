package com.weedrice.whiteboard.domain.auth.dto;

import com.weedrice.whiteboard.global.validation.PasswordStrength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetConfirmRequest {
    @NotBlank
    @Size(min = 36, max = 36)
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private String token;

    @NotBlank
    @Size(min = 8, max = 20)
    @PasswordStrength
    private String newPassword;
}
