package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.dto.NotificationMessageParamsCodec;
import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettings;
import com.weedrice.whiteboard.domain.user.repository.UserNotificationSettingsRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
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
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.Optional;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
    @Mock
    private PushNotificationDispatcher pushNotificationDispatcher;
    @Mock
    private UserSettingsRepository userSettingsRepository;
    @Mock
    private MessageSource messageSource;

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
        lenient().when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(1L, User.STATUS_ACTIVE))
                .thenReturn(Optional.of(user));

        NotificationPreferenceService preferenceService = new NotificationPreferenceService(userNotificationSettingsRepository);
        NotificationStreamPublisher streamPublisher = (userId, summary) -> {
        };
        notificationService = createNotificationService(preferenceService, streamPublisher);
    }

    @Test
    @DisplayName("Notification event is saved when setting is enabled")
    void handleNotificationEvent_success() {
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.LIKE, NotificationSourceType.POST, 1L, "Test Notification");
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        notificationService.handleNotificationEvent(event);

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("Notification content is trimmed before save")
    void handleNotificationEvent_trimsContentBeforeSave() {
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.LIKE, NotificationSourceType.POST, 1L, "  Test Notification  ");
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.handleNotificationEvent(event);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getContent()).isEqualTo("Test Notification");
    }

    @Test
    @DisplayName("Notification sourceType enum value is saved as legacy string")
    void handleNotificationEvent_savesSourceTypeEnumValue() {
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.LIKE,
                NotificationSourceType.POST, 1L, "Test Notification");
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.handleNotificationEvent(event);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getSourceType()).isEqualTo("POST");
    }

    @Test
    @DisplayName("Localized notification stores rendered legacy content and structured message metadata")
    void handleNotificationEvent_localizesAndDualWritesMessage() {
        UserSettingsRepository.SettingsReadProjection settings =
                mock(UserSettingsRepository.SettingsReadProjection.class);
        when(settings.getLanguage()).thenReturn("en");
        when(userSettingsRepository.findSettingsReadByUserId(1L)).thenReturn(Optional.of(settings));
        when(messageSource.getMessage(eq("notification.comment.created"), any(),
                eq("notification.comment.created"), eq(Locale.ENGLISH)))
                .thenReturn("Alice commented on your post.");
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreferenceService preferenceService =
                new NotificationPreferenceService(userNotificationSettingsRepository);
        NotificationCommandService commandService = new NotificationCommandService(
                notificationRepository,
                preferenceService,
                userRepository,
                pushNotificationDispatcher,
                userSettingsRepository,
                messageSource);
        NotificationEvent event = NotificationEvent.localized(user, actor, NotificationType.COMMENT,
                NotificationSourceType.POST, 1L, "notification.comment.created", "Alice");

        Notification saved = commandService.handleNotificationEvent(event);

        assertThat(saved.getContent()).isEqualTo("Alice commented on your post.");
        assertThat(saved.getMessageKey()).isEqualTo("notification.comment.created");
        assertThat(saved.getMessageParams()).isEqualTo("[\"Alice\"]");
        NotificationResponse.NotificationSummary response = NotificationResponse.NotificationSummary.from(saved);
        assertThat(response.getMessage()).isEqualTo("Alice commented on your post.");
        assertThat(response.getMessageKey()).isEqualTo("notification.comment.created");
        assertThat(response.getMessageParams()).containsExactly("Alice");
    }

    @Test
    @DisplayName("Notification message parameter decoding ignores null entries and malformed JSON")
    void decodeNotificationMessageParams_isDefensive() {
        assertThat(NotificationMessageParamsCodec.decode("[\"Alice\",null,\"Post\"]"))
                .containsExactly("Alice", "Post");
        assertThat(NotificationMessageParamsCodec.decode("not-json")).isEmpty();
    }

    @Test
    @DisplayName("Unread groupable notification is merged instead of creating a duplicate row")
    void handleNotificationEvent_mergesUnreadGroupableNotification() {
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.LIKE,
                NotificationSourceType.POST, 1L, "Updated Notification");
        when(notificationRepository.findFirstByUser_UserIdAndGroupKeyAndIsReadOrderByLastEventAtDescNotificationIdDesc(
                eq(1L), eq("1:LIKE:POST:1"), eq(false)))
                .thenReturn(Optional.of(notification));

        notificationService.handleNotificationEvent(event);

        verify(notificationRepository, never()).save(any(Notification.class));
        assertThat(notification.getContent()).isEqualTo("Updated Notification");
        assertThat(notification.getGroupCount()).isEqualTo(2);
        assertThat(notification.isGrouped()).isTrue();
    }

    @Test
    @DisplayName("Notification content is truncated to max length before save")
    void handleNotificationEvent_truncatesOverlongContentBeforeSave() {
        String content = "a".repeat(MAX_NOTIFICATION_CONTENT_LENGTH + 45);
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.LIKE, NotificationSourceType.POST, 1L, content);
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
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.LIKE, NotificationSourceType.POST, 1L, content);
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
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.LIKE, NotificationSourceType.POST, 1L, "Test Notification");
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
    @DisplayName("Notification is skipped when receiver is inactive or deleted")
    void handleNotificationEvent_inactiveReceiver_skipsSave() {
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.LIKE, NotificationSourceType.POST, 1L, "Test Notification");
        when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(1L, User.STATUS_ACTIVE))
                .thenReturn(Optional.empty());

        notificationService.handleNotificationEvent(event);

        verify(notificationRepository, never()).save(any(Notification.class));
        verifyNoInteractions(userNotificationSettingsRepository);
    }

    @Test
    @DisplayName("Notification is skipped when comment setting is disabled")
    void handleNotificationEvent_commentDisabled_skipsSave() {
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.COMMENT, NotificationSourceType.POST, 1L, "Comment Notification");
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
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.REPLY, NotificationSourceType.COMMENT, 3L, "Reply Notification");
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
                null, actor, NotificationType.LIKE, NotificationSourceType.POST, 1L, "Test Notification"));
        notificationService.handleNotificationEvent(new NotificationEvent(
                User.builder().build(), actor, NotificationType.LIKE, NotificationSourceType.POST, 1L, "Test Notification"));
        notificationService.handleNotificationEvent(new NotificationEvent(
                user, actor, null, NotificationSourceType.POST, 1L, "Test Notification"));
        notificationService.handleNotificationEvent(new NotificationEvent(
                user, actor, NotificationType.LIKE, NotificationSourceType.POST, null, "Test Notification"));
        notificationService.handleNotificationEvent(new NotificationEvent(
                user, actor, NotificationType.LIKE, null, 1L, "Test Notification"));
        notificationService.handleNotificationEvent(new NotificationEvent(
                user, actor, NotificationType.LIKE, NotificationSourceType.POST, 1L, ""));
        notificationService.handleNotificationEvent(new NotificationEvent(
                user, actor, NotificationType.LIKE, NotificationSourceType.POST, 1L, "   "));

        verifyNoInteractions(userNotificationSettingsRepository);
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("Notification save is not failed by SSE delivery exception")
    void handleNotificationEvent_deliveryFailure_doesNotPropagate(CapturedOutput output) {
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.LIKE, NotificationSourceType.POST, 1L, "Test Notification");
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        NotificationPreferenceService preferenceService = new NotificationPreferenceService(userNotificationSettingsRepository);
        NotificationStreamPublisher streamPublisher = new ThrowingNotificationStreamPublisher();
        NotificationService service = createNotificationService(preferenceService, streamPublisher);

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
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.LIKE, NotificationSourceType.POST, 1L, "Test Notification");
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        NotificationPreferenceService preferenceService = new NotificationPreferenceService(userNotificationSettingsRepository);
        RecordingNotificationStreamPublisher streamPublisher = new RecordingNotificationStreamPublisher();
        NotificationService service = createNotificationService(preferenceService, streamPublisher);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.handleNotificationEvent(event);

            assertThat(streamPublisher.delivered).isFalse();

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(streamPublisher.delivered).isTrue();
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("Web push dispatch is deferred until notification transaction commit")
    void handleNotificationEvent_defersPushDispatchUntilAfterCommit() {
        NotificationEvent event = new NotificationEvent(user, actor, NotificationType.LIKE, NotificationSourceType.POST, 1L, "Test Notification");
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        NotificationPreferenceService preferenceService = new NotificationPreferenceService(userNotificationSettingsRepository);
        NotificationCommandService commandService = new NotificationCommandService(
                notificationRepository,
                preferenceService,
                userRepository,
                pushNotificationDispatcher,
                userSettingsRepository,
                messageSource);

        TransactionSynchronizationManager.initSynchronization();
        try {
            commandService.handleNotificationEvent(event);

            verify(pushNotificationDispatcher, never()).dispatch(any(PushDispatchCommand.class));

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        ArgumentCaptor<PushDispatchCommand> pushCommandCaptor = ArgumentCaptor.forClass(PushDispatchCommand.class);
        verify(pushNotificationDispatcher).dispatch(pushCommandCaptor.capture());
        assertThat(pushCommandCaptor.getValue().userId()).isEqualTo(1L);
        assertThat(pushCommandCaptor.getValue().notificationId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Read notification marks it as read")
    void readNotification_success() {
        Long userId = 1L;
        Long notificationId = 1L;
        mockActiveUser(userId);
        when(notificationRepository.markReadByNotificationIdAndUserId(notificationId, userId)).thenReturn(1);

        notificationService.readNotification(userId, notificationId);

        verifyActiveUserValidated(userId);
        verify(notificationRepository).markReadByNotificationIdAndUserId(notificationId, userId);
    }

    @Test
    @DisplayName("Read notification keeps success for an already read owned notification")
    void readNotification_successWhenAlreadyReadByOwner() {
        Long userId = 1L;
        Long notificationId = 1L;
        mockActiveUser(userId);
        when(notificationRepository.markReadByNotificationIdAndUserId(notificationId, userId)).thenReturn(1);

        assertThatCode(() -> notificationService.readNotification(userId, notificationId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Read notification returns not found for another user's notification")
    void readNotification_notFoundForNonOwner() {
        Long userId = 1L;
        Long notificationId = 1L;
        mockActiveUser(userId);
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
        mockActiveUser(userId);
        when(notificationRepository.markReadByNotificationIdAndUserId(notificationId, userId)).thenReturn(0);

        assertThatThrownBy(() -> notificationService.readNotification(userId, notificationId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("Read notification validates user existence first")
    void readNotification_missingUser_throwsUserNotFound() {
        Long userId = 999L;
        Long notificationId = 1L;
        mockInactiveOrMissingUser(userId);

        assertThatThrownBy(() -> notificationService.readNotification(userId, notificationId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));

        verify(notificationRepository, never()).markReadByNotificationIdAndUserId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Notification list lookup succeeds")
    void getNotifications_success() {
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> notificationPage = new PageImpl<>(Collections.singletonList(notification), pageable, 1);
        mockActiveUser(userId);
        when(notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(anyLong(), any(Pageable.class))).thenReturn(notificationPage);

        NotificationResponse response = notificationService.getNotifications(userId, pageable);

        assertThat(response).isNotNull();
        verifyActiveUserValidated(userId);
    }

    @Test
    @DisplayName("SSE subscribe validates user existence")
    void validateStreamSubscription_validatesUserExists() {
        Long userId = 1L;
        mockActiveUser(userId);

        notificationService.validateStreamSubscription(userId);

        verifyActiveUserValidated(userId);
    }

    @Test
    @DisplayName("SSE subscribe rejects missing user")
    void validateStreamSubscription_missingUser_throwsUserNotFound() {
        Long userId = 999L;
        mockInactiveOrMissingUser(userId);

        assertThatThrownBy(() -> notificationService.validateStreamSubscription(userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("Notification list lookup rejects inactive or deleted user")
    void getNotifications_inactiveOrDeletedUser_throwsUserNotFound() {
        Long userId = 1L;
        mockInactiveOrMissingUser(userId);

        assertThatThrownBy(() -> notificationService.getNotifications(userId, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

        verify(notificationRepository, never())
                .findByUser_UserIdOrderByCreatedAtDesc(anyLong(), any(Pageable.class));
    }

    @Test
    @DisplayName("Notification list lookup clamps pageable in service layer")
    void getNotifications_clampsPageableInService() {
        Long userId = 1L;
        Pageable requestedPageable = PageRequest.of(0, 1000, Sort.by("unknown"));
        Page<Notification> notificationPage = new PageImpl<>(Collections.singletonList(notification), requestedPageable, 1);
        mockActiveUser(userId);
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
        mockActiveUser(userId);
        when(notificationRepository.countByUser_UserIdAndIsRead(userId, false)).thenReturn(5L);

        long count = notificationService.getUnreadNotificationCount(userId);

        assertThat(count).isEqualTo(5L);
    }

    @Test
    @DisplayName("Unread notification count rejects missing user")
    void getUnreadNotificationCount_missingUser_throwsUserNotFound() {
        Long userId = 999L;
        mockInactiveOrMissingUser(userId);

        assertThatThrownBy(() -> notificationService.getUnreadNotificationCount(userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("Read all validates user existence before bulk update")
    void readAllNotifications_validatesUserExists() {
        Long userId = 1L;
        mockActiveUser(userId);

        notificationService.readAllNotifications(userId);

        verify(notificationRepository).readAllByUserId(userId);
    }

    @Test
    @DisplayName("Read all rejects missing user before bulk update")
    void readAllNotifications_missingUser_throwsUserNotFound() {
        Long userId = 999L;
        mockInactiveOrMissingUser(userId);

        assertThatThrownBy(() -> notificationService.readAllNotifications(userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        verify(notificationRepository, never()).readAllByUserId(userId);
    }

    private void mockActiveUser(Long userId) {
        when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, User.STATUS_ACTIVE))
                .thenReturn(Optional.of(user));
    }

    private void mockInactiveOrMissingUser(Long userId) {
        when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, User.STATUS_ACTIVE))
                .thenReturn(Optional.empty());
    }

    private void verifyActiveUserValidated(Long userId) {
        verify(userRepository).findByUserIdAndStatusAndDeletedAtIsNull(userId, User.STATUS_ACTIVE);
    }

    private NotificationService createNotificationService(NotificationPreferenceService preferenceService,
                                                          NotificationStreamPublisher streamPublisher) {
        NotificationCommandService commandService = new NotificationCommandService(
                notificationRepository,
                preferenceService,
                userRepository,
                pushNotificationDispatcher,
                userSettingsRepository,
                messageSource);
        NotificationDeliveryPublisher deliveryPublisher = new NotificationDeliveryPublisher(
                streamPublisher,
                notifications -> Collections.emptyMap());
        NotificationQueryService queryService = new NotificationQueryService(
                notificationRepository,
                notifications -> Collections.emptyMap());
        NotificationReadCommandService readCommandService =
                new NotificationReadCommandService(commandService);
        return new NotificationService(
                queryService,
                readCommandService,
                userRepository,
                commandService,
                deliveryPublisher);
    }

    private static class ThrowingNotificationStreamPublisher implements NotificationStreamPublisher {

        @Override
        public void publish(Long userId, NotificationResponse.NotificationSummary summary) {
            throw new IllegalStateException("delivery failed");
        }
    }

    private static class RecordingNotificationStreamPublisher implements NotificationStreamPublisher {

        private boolean delivered;

        @Override
        public void publish(Long userId, NotificationResponse.NotificationSummary summary) {
            delivered = true;
        }
    }
}

