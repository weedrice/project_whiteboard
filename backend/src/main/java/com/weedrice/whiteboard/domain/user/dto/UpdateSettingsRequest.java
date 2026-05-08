package com.weedrice.whiteboard.domain.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateSettingsRequest {
    @Size(max = 20)
    private String theme;
    @Size(max = 10)
    private String language;
    @Size(max = 50)
    private String timezone;
    private Boolean hideNsfw;
}
