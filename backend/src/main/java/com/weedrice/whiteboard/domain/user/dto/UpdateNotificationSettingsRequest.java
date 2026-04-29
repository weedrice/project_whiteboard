package com.weedrice.whiteboard.domain.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UpdateNotificationSettingsRequest {
    @NotEmpty
    private List<@NotNull @Valid UpdateNotificationSettingItem> settings;
}
