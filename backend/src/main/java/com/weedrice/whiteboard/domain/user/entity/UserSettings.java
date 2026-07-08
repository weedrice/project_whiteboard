package com.weedrice.whiteboard.domain.user.entity;

import com.weedrice.whiteboard.global.common.converter.BooleanToYNConverter;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSettings extends BaseTimeEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "theme", nullable = false, length = 20)
    private String theme;

    @Column(name = "language", nullable = false, length = 10)
    private String language;

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "hide_nsfw", nullable = false, length = 1)
    private Boolean hideNsfw;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "push_enabled", nullable = false, length = 1)
    private Boolean pushEnabled;

    @Column(name = "onboarding_completed_at")
    private LocalDateTime onboardingCompletedAt;

    @Builder
    public UserSettings(User user) {
        this.user = user;
        this.theme = "LIGHT";
        this.language = "ko";
        this.timezone = "Asia/Seoul";
        this.hideNsfw = true;
        this.pushEnabled = false;
    }

    public void updateSettings(String theme, String language, String timezone, Boolean hideNsfw) {
        if (theme != null) {
            this.theme = theme;
        }
        if (language != null) {
            this.language = language;
        }
        if (timezone != null) {
            this.timezone = timezone;
        }
        if (hideNsfw != null) {
            this.hideNsfw = hideNsfw;
        }
    }

    public void setPushEnabled(Boolean pushEnabled) {
        if (pushEnabled != null) {
            this.pushEnabled = pushEnabled;
        }
    }

    public void completeOnboarding(LocalDateTime completedAt) {
        this.onboardingCompletedAt = completedAt;
    }
}
