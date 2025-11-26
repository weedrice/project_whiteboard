package com.weedrice.whiteboard.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateNotificationSettingRequest {
    @NotBlank(message = "알림 타입은 필수입니다")
    private String notificationType;

    private Boolean isEnabled;
}
