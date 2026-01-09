package com.weedrice.whiteboard.domain.auth.dto;

import com.weedrice.whiteboard.global.validation.NoHtml;
import com.weedrice.whiteboard.global.validation.PasswordStrength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignupRequest {
    @NotBlank
    @Size(min = 4, max = 30)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "아이디는 영문, 숫자, 언더스코어만 사용 가능합니다")
    @NoHtml
    private String loginId;

    @NotBlank
    @Size(min = 8, max = 20)
    @PasswordStrength
    private String password;

    @NotBlank
    @Email
    @NoHtml
    private String email;

    @NotBlank
    @Size(min = 2, max = 50)
    @NoHtml
    private String displayName;

    private String provider;
    private String providerId;
}
