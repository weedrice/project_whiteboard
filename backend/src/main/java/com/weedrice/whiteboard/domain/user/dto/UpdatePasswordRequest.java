package com.weedrice.whiteboard.domain.user.dto;

import com.weedrice.whiteboard.global.validation.PasswordStrength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdatePasswordRequest {
    @NotBlank
    @Size(max = 20)
    private String currentPassword;

    @NotBlank
    @Size(min = 8, max = 20)
    @PasswordStrength
    private String newPassword;
}
