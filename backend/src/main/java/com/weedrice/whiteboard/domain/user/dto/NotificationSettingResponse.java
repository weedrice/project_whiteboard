package com.weedrice.whiteboard.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NotificationSettingResponse {
    private String notificationType;

    @Getter(onMethod_ = @JsonProperty("isEnabled"))
    private boolean isEnabled;
}
