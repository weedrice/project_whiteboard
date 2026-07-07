package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettings;
import com.weedrice.whiteboard.domain.user.repository.UserNotificationSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    @Mock
    private UserNotificationSettingsRepository userNotificationSettingsRepository;

    private NotificationPreferenceService notificationPreferenceService;

    private User receiver;
    private User actor;

    @BeforeEach
    void setUp() {
        notificationPreferenceService = new NotificationPreferenceService(userNotificationSettingsRepository);

        receiver = User.builder().build();
        ReflectionTestUtils.setField(receiver, "userId", 1L);

        actor = User.builder().build();
        ReflectionTestUtils.setField(actor, "userId", 2L);
    }

    @Test
    @DisplayName("?먭린 ?먯떊?먭쾶 蹂대궡???뚮┝? self-notification?쇰줈 ?먮떒?쒕떎")
    void isSelfNotification_true() {
        NotificationEvent event = new NotificationEvent(receiver, receiver, NotificationType.LIKE, NotificationSourceType.POST, 1L, "like");

        assertThat(notificationPreferenceService.isSelfNotification(event)).isTrue();
    }

    @Test
    @DisplayName("Self-notification check is false when receiver id is missing")
    void isSelfNotification_falseWhenReceiverIdMissing() {
        User receiverWithoutId = User.builder().build();
        NotificationEvent event = new NotificationEvent(
                receiverWithoutId, actor, NotificationType.LIKE, NotificationSourceType.POST, 1L, "like");

        assertThat(notificationPreferenceService.isSelfNotification(event)).isFalse();
    }

    @Test
    @DisplayName("鍮꾪솢?깊솕???뚮┝ ??낆? false瑜?諛섑솚?쒕떎")
    void isNotificationEnabled_falseWhenDisabled() {
        UserNotificationSettings setting = UserNotificationSettings.builder()
                .userId(1L)
                .notificationType(NotificationType.COMMENT)
                .isEnabled(false)
                .build();
        when(userNotificationSettingsRepository.findByUserIdAndNotificationType(1L, NotificationType.COMMENT))
                .thenReturn(Optional.of(setting));

        NotificationEvent event = new NotificationEvent(receiver, actor, NotificationType.COMMENT, NotificationSourceType.POST, 1L, "comment");

        assertThat(notificationPreferenceService.isNotificationEnabled(event)).isFalse();
    }

    @Test
    @DisplayName("Notification is disabled when required fields are missing")
    void isNotificationEnabled_falseWhenRequiredFieldsMissing() {
        NotificationEvent missingReceiver = new NotificationEvent(
                null, actor, NotificationType.COMMENT, NotificationSourceType.POST, 1L, "comment");
        NotificationEvent missingReceiverId = new NotificationEvent(
                User.builder().build(), actor, NotificationType.COMMENT, NotificationSourceType.POST, 1L, "comment");
        NotificationEvent missingType = new NotificationEvent(
                receiver, actor, null, NotificationSourceType.POST, 1L, "comment");

        assertThat(notificationPreferenceService.isNotificationEnabled(null)).isFalse();
        assertThat(notificationPreferenceService.isNotificationEnabled(missingReceiver)).isFalse();
        assertThat(notificationPreferenceService.isNotificationEnabled(missingReceiverId)).isFalse();
        assertThat(notificationPreferenceService.isNotificationEnabled(missingType)).isFalse();

        verifyNoInteractions(userNotificationSettingsRepository);
    }
}
