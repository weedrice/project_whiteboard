package com.weedrice.whiteboard.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeleteAccountRequest {
    @NotBlank(message = "{validation.user.password.required}")
    @Size(max = 20)
    private String password;
}
