package com.weedrice.whiteboard.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserSettingsResponse {
    private String theme;
    private String language;
    private String timezone;
    private boolean hideNsfw;
}
