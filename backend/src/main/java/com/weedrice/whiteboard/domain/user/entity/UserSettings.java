package com.weedrice.whiteboard.domain.user.entity;

import com.weedrice.whiteboard.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(name = "hide_nsfw", nullable = false, length = 1)
    private String hideNsfw;

    @Builder
    public UserSettings(User user) {
        this.user = user;
        this.theme = "LIGHT";
        this.language = "ko";
        this.timezone = "Asia/Seoul";
        this.hideNsfw = "Y";
    }

    public void updateSettings(String theme, String language, String timezone, String hideNsfw) {
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
}
