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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class NotificationServiceTest {
    private static final int MAX_NOTIFICATION_CONTENT_LENGTH = 255;

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
        NotificationStreamService streamService = new NotificationStreamService(1_800_000L, 5);
        notificationService = createNotificationService(preferenceService, streamService);
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
    @DisplayName("Notification content is trimmed before save")
    void handleNotificationEvent_trimsContentBeforeSave() {
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.LIKE, "POST", 1L, "  Test Notification  ");
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.handleNotificationEvent(event);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getContent()).isEqualTo("Test Notification");
    }

    @Test
    @DisplayName("Notification content is truncated to max length before save")
    void handleNotificationEvent_truncatesOverlongContentBeforeSave() {
        String content = "a".repeat(MAX_NOTIFICATION_CONTENT_LENGTH + 45);
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.LIKE, "POST", 1L, content);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.handleNotificationEvent(event);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getContent()).hasSize(MAX_NOTIFICATION_CONTENT_LENGTH);
    }

    @Test
    @DisplayName("Notification content truncation preserves supplementary characters")
    void handleNotificationEvent_truncatesByCodePointBeforeSave() {
        String supplementaryCharacter = new String(Character.toChars(0x1F600));
        String content = "a".repeat(MAX_NOTIFICATION_CONTENT_LENGTH - 1) + supplementaryCharacter + "b";
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.LIKE, "POST", 1L, content);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.handleNotificationEvent(event);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        String savedContent = notificationCaptor.getValue().getContent();
        assertThat(savedContent.codePointCount(0, savedContent.length())).isEqualTo(MAX_NOTIFICATION_CONTENT_LENGTH);
        assertThat(savedContent).endsWith(supplementaryCharacter);
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
    @DisplayName("Notification is skipped when required event payload is missing")
    void handleNotificationEvent_missingRequiredPayload_skipsSave() {
        notificationService.handleNotificationEvent(null);
        notificationService.handleNotificationEvent(new NotificationEvent(
                null, actor, NotificationType.LIKE, "POST", 1L, "Test Notification"));
        notificationService.handleNotificationEvent(new NotificationEvent(
                User.builder().build(), actor, NotificationType.LIKE, "POST", 1L, "Test Notification"));
        notificationService.handleNotificationEvent(new NotificationEvent(
                user, actor, null, "POST", 1L, "Test Notification"));
        notificationService.handleNotificationEvent(new NotificationEvent(
                user, actor, NotificationType.LIKE, " ", 1L, "Test Notification"));
        notificationService.handleNotificationEvent(new NotificationEvent(
                user, actor, NotificationType.LIKE, "POST", 1L, ""));
        notificationService.handleNotificationEvent(new NotificationEvent(
                user, actor, NotificationType.LIKE, "POST", 1L, "   "));

        verifyNoInteractions(userNotificationSettingsRepository);
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("Notification save is not failed by SSE delivery exception")
    void handleNotificationEvent_deliveryFailure_doesNotPropagate(CapturedOutput output) {
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.LIKE, "POST", 1L, "Test Notification");
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        NotificationPreferenceService preferenceService = new NotificationPreferenceService(userNotificationSettingsRepository);
        NotificationStreamService streamService = new ThrowingNotificationStreamService();
        NotificationService service = createNotificationService(preferenceService, streamService);

        assertThatCode(() -> service.handleNotificationEvent(event)).doesNotThrowAnyException();

        verify(notificationRepository).save(any(Notification.class));
        assertThat(output.getAll())
                .contains("Failed to deliver notification SSE")
                .contains("userId=1")
                .contains("notificationId=1")
                .contains("IllegalStateException");
    }

    @Test
    @DisplayName("Notification SSE delivery is deferred until transaction commit")
    void handleNotificationEvent_defersDeliveryUntilAfterCommit() {
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.LIKE, "POST", 1L, "Test Notification");
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        NotificationPreferenceService preferenceService = new NotificationPreferenceService(userNotificationSettingsRepository);
        RecordingNotificationStreamService streamService = new RecordingNotificationStreamService();
        NotificationService service = createNotificationService(preferenceService, streamService);

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
    @DisplayName("Read notification keeps success for an already read owned notification")
    void readNotification_successWhenAlreadyReadByOwner() {
        Long userId = 1L;
        Long notificationId = 1L;
        when(notificationRepository.markReadByNotificationIdAndUserId(notificationId, userId)).thenReturn(1);

        assertThatCode(() -> notificationService.readNotification(userId, notificationId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Read notification returns not found for another user's notification")
    void readNotification_notFoundForNonOwner() {
        Long userId = 1L;
        Long notificationId = 1L;
        when(notificationRepository.markReadByNotificationIdAndUserId(notificationId, userId)).thenReturn(0);

        assertThatThrownBy(() -> notificationService.readNotification(userId, notificationId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("Read notification returns not found for a missing notification")
    void readNotification_notFoundWhenMissing() {
        Long userId = 1L;
        Long notificationId = 999L;
        when(notificationRepository.markReadByNotificationIdAndUserId(notificationId, userId)).thenReturn(0);

        assertThatThrownBy(() -> notificationService.readNotification(userId, notificationId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
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
    @DisplayName("SSE subscribe validates user existence")
    void subscribe_validatesUserExists() {
        Long userId = 1L;
        when(userRepository.existsById(userId)).thenReturn(true);

        assertThat(notificationService.subscribe(userId)).isNotNull();

        verify(userRepository).existsById(userId);
    }

    @Test
    @DisplayName("SSE subscribe rejects missing user")
    void subscribe_missingUser_throwsUserNotFound() {
        Long userId = 999L;
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThatThrownBy(() -> notificationService.subscribe(userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("Notification list lookup clamps pageable in service layer")
    void getNotifications_clampsPageableInService() {
        Long userId = 1L;
        Pageable requestedPageable = PageRequest.of(0, 1000, Sort.by("unknown"));
        Page<Notification> notificationPage = new PageImpl<>(Collections.singletonList(notification), requestedPageable, 1);
        when(userRepository.existsById(userId)).thenReturn(true);
        when(notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(anyLong(), any(Pageable.class))).thenReturn(notificationPage);

        notificationService.getNotifications(userId, requestedPageable);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByUser_UserIdOrderByCreatedAtDesc(eq(userId), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(captor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(captor.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(captor.getValue().getSort().getOrderFor("notificationId")).isNotNull();
        assertThat(captor.getValue().getSort().getOrderFor("notificationId").isDescending()).isTrue();
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
    @DisplayName("Unread notification count rejects missing user")
    void getUnreadNotificationCount_missingUser_throwsUserNotFound() {
        Long userId = 999L;
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThatThrownBy(() -> notificationService.getUnreadNotificationCount(userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("Read all validates user existence before bulk update")
    void readAllNotifications_validatesUserExists() {
        Long userId = 1L;
        when(userRepository.existsById(userId)).thenReturn(true);

        notificationService.readAllNotifications(userId);

        verify(notificationRepository).readAllByUserId(userId);
    }

    @Test
    @DisplayName("Read all rejects missing user before bulk update")
    void readAllNotifications_missingUser_throwsUserNotFound() {
        Long userId = 999L;
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThatThrownBy(() -> notificationService.readAllNotifications(userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        verify(notificationRepository, never()).readAllByUserId(userId);
    }

    private NotificationService createNotificationService(NotificationPreferenceService preferenceService,
                                                          NotificationStreamService streamService) {
        NotificationCommandService commandService = new NotificationCommandService(
                notificationRepository,
                preferenceService);
        NotificationEventHandler eventHandler = new NotificationEventHandler(commandService, streamService);
        NotificationQueryService queryService = new NotificationQueryService(notificationRepository);
        NotificationReadCommandService readCommandService =
                new NotificationReadCommandService(commandService);
        NotificationSseFacade sseFacade = new NotificationSseFacade(streamService);
        return new NotificationService(eventHandler, queryService, readCommandService, sseFacade, userRepository);
    }

    private static class ThrowingNotificationStreamService extends NotificationStreamService {

        private ThrowingNotificationStreamService() {
            super(1_800_000L, 5);
        }

        @Override
        void deliverNotification(Long userId, Notification notification) {
            throw new IllegalStateException("delivery failed");
        }
    }

    private static class RecordingNotificationStreamService extends NotificationStreamService {

        private boolean delivered;

        private RecordingNotificationStreamService() {
            super(1_800_000L, 5);
        }

        @Override
        void deliverNotification(Long userId, Notification notification) {
            delivered = true;
        }
    }
}
