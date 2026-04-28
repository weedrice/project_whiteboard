package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettings;
import com.weedrice.whiteboard.domain.user.repository.UserNotificationSettingsRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserNotificationSettingsRepository userNotificationSettingsRepository;

    private NotificationService notificationService;

    private User user;
    private User actor;
    private Notification notification;

    @BeforeEach
    void setUp() {
        user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        actor = User.builder().build();
        ReflectionTestUtils.setField(actor, "userId", 2L);

        notification = Notification.builder()
                .user(user)
                .actor(actor)
                .notificationType(NotificationType.LIKE)
                .sourceType("POST")
                .sourceId(1L)
                .content("Test Notification")
                .build();
        ReflectionTestUtils.setField(notification, "notificationId", 1L);

        lenient().when(userNotificationSettingsRepository.findByUserIdAndNotificationType(anyLong(), any(NotificationType.class)))
                .thenReturn(Optional.empty());

        NotificationPreferenceService preferenceService = new NotificationPreferenceService(userNotificationSettingsRepository);
        NotificationStreamService streamService = new NotificationStreamService();
        NotificationCommandService commandService = new NotificationCommandService(
                notificationRepository,
                preferenceService);
        notificationService = new NotificationService(notificationRepository, userRepository, commandService, streamService);
    }

    @Test
    @DisplayName("Notification event is saved when setting is enabled")
    void handleNotificationEvent_success() {
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.LIKE, "POST", 1L, "Test Notification");
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        notificationService.handleNotificationEvent(event);

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("Notification is skipped when like setting is disabled")
    void handleNotificationEvent_disabledSetting_skipsSave() {
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.LIKE, "POST", 1L, "Test Notification");
        UserNotificationSettings setting = UserNotificationSettings.builder()
                .userId(1L)
                .notificationType(NotificationType.LIKE)
                .isEnabled(false)
                .build();
        when(userNotificationSettingsRepository.findByUserIdAndNotificationType(1L, NotificationType.LIKE))
                .thenReturn(Optional.of(setting));

        notificationService.handleNotificationEvent(event);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("Notification is skipped when comment setting is disabled")
    void handleNotificationEvent_commentDisabled_skipsSave() {
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.COMMENT, "POST", 1L, "Comment Notification");
        UserNotificationSettings setting = UserNotificationSettings.builder()
                .userId(1L)
                .notificationType(NotificationType.COMMENT)
                .isEnabled(false)
                .build();
        when(userNotificationSettingsRepository.findByUserIdAndNotificationType(1L, NotificationType.COMMENT))
                .thenReturn(Optional.of(setting));

        notificationService.handleNotificationEvent(event);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("Notification is saved when no explicit setting exists")
    void handleNotificationEvent_missingSetting_savesNotification() {
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.REPLY, "COMMENT", 3L, "Reply Notification");
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        notificationService.handleNotificationEvent(event);

        verify(userNotificationSettingsRepository).findByUserIdAndNotificationType(1L, NotificationType.REPLY);
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("Notification save is not failed by SSE delivery exception")
    void handleNotificationEvent_deliveryFailure_doesNotPropagate() {
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.LIKE, "POST", 1L, "Test Notification");
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        NotificationPreferenceService preferenceService = new NotificationPreferenceService(userNotificationSettingsRepository);
        NotificationStreamService streamService = new ThrowingNotificationStreamService();
        NotificationCommandService commandService = new NotificationCommandService(
                notificationRepository,
                preferenceService);
        NotificationService service = new NotificationService(notificationRepository, userRepository, commandService, streamService);

        assertThatCode(() -> service.handleNotificationEvent(event)).doesNotThrowAnyException();

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("Notification SSE delivery is deferred until transaction commit")
    void handleNotificationEvent_defersDeliveryUntilAfterCommit() {
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.LIKE, "POST", 1L, "Test Notification");
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        NotificationPreferenceService preferenceService = new NotificationPreferenceService(userNotificationSettingsRepository);
        RecordingNotificationStreamService streamService = new RecordingNotificationStreamService();
        NotificationCommandService commandService = new NotificationCommandService(
                notificationRepository,
                preferenceService);
        NotificationService service = new NotificationService(notificationRepository, userRepository, commandService, streamService);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.handleNotificationEvent(event);

            assertThat(streamService.delivered).isFalse();

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(streamService.delivered).isTrue();
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("Read notification marks it as read")
    void readNotification_success() {
        Long userId = 1L;
        Long notificationId = 1L;
        when(notificationRepository.markReadByNotificationIdAndUserId(notificationId, userId)).thenReturn(1);

        notificationService.readNotification(userId, notificationId);

        verify(notificationRepository).markReadByNotificationIdAndUserId(notificationId, userId);
    }

    @Test
    @DisplayName("Read notification keeps forbidden semantics for another user's notification")
    void readNotification_forbidden() {
        Long userId = 1L;
        Long notificationId = 1L;
        when(notificationRepository.markReadByNotificationIdAndUserId(notificationId, userId)).thenReturn(0);
        when(notificationRepository.existsByNotificationIdAndUser_UserId(notificationId, userId)).thenReturn(false);
        when(notificationRepository.existsById(notificationId)).thenReturn(true);

        assertThatThrownBy(() -> notificationService.readNotification(userId, notificationId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("Notification list lookup succeeds")
    void getNotifications_success() {
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> notificationPage = new PageImpl<>(Collections.singletonList(notification), pageable, 1);
        when(userRepository.existsById(userId)).thenReturn(true);
        when(notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(anyLong(), any(Pageable.class))).thenReturn(notificationPage);

        NotificationResponse response = notificationService.getNotifications(userId, pageable);

        assertThat(response).isNotNull();
        verify(userRepository).existsById(userId);
    }

    @Test
    @DisplayName("Notification list lookup clamps pageable in service layer")
    void getNotifications_clampsPageableInService() {
        Long userId = 1L;
        Pageable requestedPageable = PageRequest.of(0, 1000, Sort.by("notificationId"));
        Page<Notification> notificationPage = new PageImpl<>(Collections.singletonList(notification), requestedPageable, 1);
        when(userRepository.existsById(userId)).thenReturn(true);
        when(notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(anyLong(), any(Pageable.class))).thenReturn(notificationPage);

        notificationService.getNotifications(userId, requestedPageable);

        org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByUser_UserIdOrderByCreatedAtDesc(eq(userId), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(captor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(captor.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(captor.getValue().getSort().getOrderFor("notificationId")).isNull();
    }

    @Test
    @DisplayName("Unread notification count lookup succeeds")
    void getUnreadNotificationCount_success() {
        Long userId = 1L;
        when(userRepository.existsById(userId)).thenReturn(true);
        when(notificationRepository.countByUser_UserIdAndIsRead(userId, false)).thenReturn(5L);

        long count = notificationService.getUnreadNotificationCount(userId);

        assertThat(count).isEqualTo(5L);
    }

    @Test
    @DisplayName("Read all validates user existence before bulk update")
    void readAllNotifications_validatesUserExists() {
        Long userId = 1L;
        when(userRepository.existsById(userId)).thenReturn(true);

        notificationService.readAllNotifications(userId);

        verify(notificationRepository).readAllByUserId(userId);
    }

    private static class ThrowingNotificationStreamService extends NotificationStreamService {

        @Override
        void deliverNotification(Long userId, Notification notification) {
            throw new IllegalStateException("delivery failed");
        }
    }

    private static class RecordingNotificationStreamService extends NotificationStreamService {

        private boolean delivered;

        @Override
        void deliverNotification(Long userId, Notification notification) {
            delivered = true;
        }
    }
}
