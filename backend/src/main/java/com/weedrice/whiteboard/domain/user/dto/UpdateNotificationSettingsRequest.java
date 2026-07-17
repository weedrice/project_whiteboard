package com.weedrice.whiteboard.domain.user.dto;

import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateNotificationSettingsRequest {
    @NotEmpty
    @Size(max = NotificationType.SUPPORTED_TYPE_COUNT)
    private List<@NotNull @Valid UpdateNotificationSettingItem> settings;
}
