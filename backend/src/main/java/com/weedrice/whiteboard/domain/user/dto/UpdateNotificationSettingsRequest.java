package com.weedrice.whiteboard.domain.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class UpdateNotificationSettingsRequest {
    @Valid
    @NotEmpty
    private List<UpdateNotificationSettingItem> settings;
}
