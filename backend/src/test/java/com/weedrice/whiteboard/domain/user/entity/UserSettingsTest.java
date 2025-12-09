package com.weedrice.whiteboard.domain.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserSettingsTest {

    @Test
    @DisplayName("UserSettings 생성 시 기본값 설정 성공")
    void createUserSettings_setsDefaultValues() {
        // given
        User user = User.builder()
                .loginId("testuser")
                .password("password")
                .email("test@example.com")
                .displayName("Test User")
                .build();

        // when
        UserSettings settings = UserSettings.builder()
                .user(user)
                .build();

        // then
        assertThat(settings.getUser()).isEqualTo(user);
        assertThat(settings.getTheme()).isEqualTo("LIGHT");
        assertThat(settings.getLanguage()).isEqualTo("ko");
        assertThat(settings.getTimezone()).isEqualTo("Asia/Seoul");
        assertThat(settings.getHideNsfw()).isTrue();
    }

    @Test
    @DisplayName("UserSettings 업데이트 성공")
    void updateSettings_success() {
        // given
        User user = User.builder().build();
        UserSettings settings = UserSettings.builder().user(user).build();

        // when
        settings.updateSettings("DARK", "en", "UTC", false);

        // then
        assertThat(settings.getTheme()).isEqualTo("DARK");
        assertThat(settings.getLanguage()).isEqualTo("en");
        assertThat(settings.getTimezone()).isEqualTo("UTC");
        assertThat(settings.getHideNsfw()).isFalse();
    }
}
