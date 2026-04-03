package com.weedrice.whiteboard.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateNotificationSettingRequest {
    @NotBlank(message = "{validation.notification.type.required}")
    private String notificationType;

    @NotNull
    private Boolean isEnabled;
}
