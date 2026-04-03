package com.weedrice.whiteboard.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNotificationSettingItem {
    @NotBlank(message = "{validation.notification.type.required}")
    private String notificationType;

    @NotNull
    private Boolean isEnabled;
}
