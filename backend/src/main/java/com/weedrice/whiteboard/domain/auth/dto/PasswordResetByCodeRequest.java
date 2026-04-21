package com.weedrice.whiteboard.domain.auth.dto;

import com.weedrice.whiteboard.global.validation.NoHtml;
import com.weedrice.whiteboard.global.validation.PasswordStrength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetByCodeRequest {
    @NotBlank
    @Email
    @NoHtml
    private String email;

    @NotBlank
    @Size(max = 64)
    @NoHtml
    private String verificationTicket;

    @NotBlank
    @Size(min = 8, max = 20)
    @PasswordStrength
    private String newPassword;
}
