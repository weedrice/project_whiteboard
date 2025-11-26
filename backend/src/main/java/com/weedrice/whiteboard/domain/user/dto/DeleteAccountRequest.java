package com.weedrice.whiteboard.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteAccountRequest {
    @NotBlank(message = "비밀번호는 필수입니다")
    private String password;
}
