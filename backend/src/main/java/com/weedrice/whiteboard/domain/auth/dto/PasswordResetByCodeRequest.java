package com.weedrice.whiteboard.domain.auth.dto;

import com.weedrice.whiteboard.global.validation.NoHtml;
import com.weedrice.whiteboard.global.validation.PasswordStrength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
    @Size(min = 6, max = 6, message = "인증 코드는 6자리여야 합니다")
    @Pattern(regexp = "^[0-9]{6}$", message = "인증 코드는 숫자 6자리여야 합니다")
    private String code;

    @NotBlank
    @Size(min = 8, max = 20)
    @PasswordStrength
    private String newPassword;
}
