package com.weedrice.whiteboard.domain.user.entity;

import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserNotificationSettingsTest {

    @Test
    @DisplayName("UserNotificationSettings 생성 성공")
    void createUserNotificationSettings_success() {
        // given
        Long userId = 1L;
        NotificationType notificationType = NotificationType.LIKE;
        boolean isEnabled = true;

        // when
        UserNotificationSettings settings = UserNotificationSettings.builder()
                .userId(userId)
                .notificationType(notificationType)
                .isEnabled(isEnabled)
                .build();

        // then
        assertThat(settings.getUserId()).isEqualTo(userId);
        assertThat(settings.getNotificationType()).isEqualTo(notificationType);
        assertThat(settings.getIsEnabled()).isEqualTo(isEnabled);
    }

    @Test
    @DisplayName("setEnabled 메서드 동작 검증")
    void setEnabled_updatesState() {
        // given
        UserNotificationSettings settings = UserNotificationSettings.builder()
                .userId(1L)
                .notificationType(NotificationType.LIKE)
                .isEnabled(true)
                .build();

        // when
        settings.setEnabled(false);

        // then
        assertThat(settings.getIsEnabled()).isFalse();
    }

    @Test
    @DisplayName("UserNotificationSettingsId 동등성 비교")
    void userNotificationSettingsId_equalsAndHashCode() {
        // given
        UserNotificationSettingsId id1 = new UserNotificationSettingsId(1L, NotificationType.LIKE);
        UserNotificationSettingsId id2 = new UserNotificationSettingsId(1L, NotificationType.LIKE);
        UserNotificationSettingsId id3 = new UserNotificationSettingsId(2L, NotificationType.LIKE);
        UserNotificationSettingsId id4 = new UserNotificationSettingsId(1L, NotificationType.COMMENT);

        // then
        assertThat(id1).isEqualTo(id2);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        assertThat(id1).isNotEqualTo(id3);
        assertThat(id1).isNotEqualTo(id4);
    }
}
