package com.weedrice.whiteboard.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateNotificationSettingRequest {
    @NotBlank(message = "{validation.notification.type.required}")
    private String notificationType;

    private Boolean isEnabled;
}
