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
    private boolean profileOverviewPublic;
    private boolean profilePostsPublic;
    private boolean profileCommentsPublic;
    private boolean profilePrivacyEnabled;

    public UserSettingsResponse(String theme, String language, String timezone, boolean hideNsfw,
            boolean pushEnabled, LocalDateTime onboardingCompletedAt) {
        this(theme, language, timezone, hideNsfw, pushEnabled, onboardingCompletedAt, true, true, true, false);
    }

    public UserSettingsResponse(String theme, String language, String timezone, boolean hideNsfw,
            boolean pushEnabled, LocalDateTime onboardingCompletedAt, boolean profileOverviewPublic,
            boolean profilePostsPublic, boolean profileCommentsPublic) {
        this(theme, language, timezone, hideNsfw, pushEnabled, onboardingCompletedAt,
                profileOverviewPublic, profilePostsPublic, profileCommentsPublic, false);
    }
}
