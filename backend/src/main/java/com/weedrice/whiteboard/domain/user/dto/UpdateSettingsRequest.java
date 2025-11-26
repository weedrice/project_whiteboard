package com.weedrice.whiteboard.domain.user.dto;

import lombok.Data;

@Data
public class UpdateSettingsRequest {
    private String theme;
    private String language;
    private String timezone;
    private Boolean hideNsfw;
}
