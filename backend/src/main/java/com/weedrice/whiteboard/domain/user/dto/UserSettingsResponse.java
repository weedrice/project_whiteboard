package com.weedrice.whiteboard.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class UserSettingsResponse {
    private String theme;
    private String language;
    private String timezone;
    private boolean hideNsfw;
    private boolean pushEnabled;
    private LocalDateTime onboardingCompletedAt;
}
