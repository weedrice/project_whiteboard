package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.dto.NotificationSettingResponse;
import com.weedrice.whiteboard.domain.user.dto.UpdateNotificationSettingItem;
import com.weedrice.whiteboard.domain.user.dto.UserSettingsResponse;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettings;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.UserNotificationSettingsRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSettingsServiceTest {

    private UserSettingsService userSettingsService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSettingsRepository userSettingsRepository;

    @Mock
    private UserNotificationSettingsRepository userNotificationSettingsRepository;

    @Mock
    private SanctionService sanctionService;

    @Mock
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        UserWritableResolver userWritableResolver = new UserWritableResolver(userRepository, sanctionService);
        userSettingsService = new UserSettingsService(
                userRepository,
                userSettingsRepository,
                userNotificationSettingsRepository,
                entityManager,
                userWritableResolver);
    }

    @Test
    @DisplayName("Settings lookup succeeds")
    void getSettings_success() {
        User user = User.builder().build();
        UserSettings settings = new UserSettings(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));

        UserSettingsResponse response = userSettingsService.getSettings(1L);

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("Settings lookup returns defaults when settings row is missing")
    void getSettings_missingRow_returnsDefaults() {
        User user = User.builder().build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.empty());

        UserSettingsResponse response = userSettingsService.getSettings(1L);

        assertThat(response.getTheme()).isEqualTo("LIGHT");
        assertThat(response.getLanguage()).isEqualTo("ko");
        assertThat(response.getTimezone()).isEqualTo("Asia/Seoul");
        assertThat(response.isHideNsfw()).isTrue();
        verify(userSettingsRepository, never()).save(any());
    }

    @Test
    @DisplayName("Settings update succeeds")
    void updateSettings_success() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        UserSettings settings = new UserSettings(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));
        when(userSettingsRepository.save(any())).thenReturn(settings);

        UserSettingsResponse response = userSettingsService.updateSettings(1L, " dark ", " EN ", " UTC ", true);

        assertThat(response.getTheme()).isEqualTo("DARK");
        assertThat(response.getLanguage()).isEqualTo("en");
        assertThat(response.getTimezone()).isEqualTo("UTC");
        assertThat(response.isHideNsfw()).isTrue();
        verify(userRepository).findById(1L);
    }

    @Test
    @DisplayName("Settings update rejects unsupported theme")
    void updateSettings_invalidTheme() {
        assertThatThrownBy(() -> userSettingsService.updateSettings(1L, "SYSTEM", null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(userRepository, never()).findById(any());
        verify(userSettingsRepository, never()).save(any());
    }

    @Test
    @DisplayName("Settings update rejects unsupported language")
    void updateSettings_invalidLanguage() {
        assertThatThrownBy(() -> userSettingsService.updateSettings(1L, null, "fr", null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(userRepository, never()).findById(any());
        verify(userSettingsRepository, never()).save(any());
    }

    @Test
    @DisplayName("Settings update rejects invalid timezone")
    void updateSettings_invalidTimezone() {
        assertThatThrownBy(() -> userSettingsService.updateSettings(1L, null, null, "Not/AZone", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(userRepository, never()).findById(any());
        verify(userSettingsRepository, never()).save(any());
    }

    @Test
    @DisplayName("Settings update rejects blank timezone")
    void updateSettings_blankTimezone() {
        assertThatThrownBy(() -> userSettingsService.updateSettings(1L, null, null, "   ", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(userRepository, never()).findById(any());
        verify(userSettingsRepository, never()).save(any());
    }

    @Test
    @DisplayName("Settings row creation recovers when another request creates the row first")
    void getOrCreateSettingsEntity_recoversAfterDuplicateInsert() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        UserSettings settings = new UserSettings(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.empty(), Optional.of(settings));
        when(userSettingsRepository.saveAndFlush(any(UserSettings.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate user_settings"));

        UserSettings result = userSettingsService.getOrCreateSettingsEntity(1L);

        assertThat(result).isSameAs(settings);
        verify(entityManager).clear();
    }

    @Test
    @DisplayName("Settings update recovers when another request creates the row first")
    void updateSettingsEntity_recoversAfterDuplicateInsert() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        UserSettings settings = new UserSettings(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.empty(), Optional.of(settings));
        when(userSettingsRepository.saveAndFlush(any(UserSettings.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate user_settings"));
        when(userSettingsRepository.save(settings)).thenReturn(settings);

        UserSettings result = userSettingsService.updateSettingsEntity(1L, "DARK", "en", "UTC", false);

        assertThat(result.getTheme()).isEqualTo("DARK");
        assertThat(result.getLanguage()).isEqualTo("en");
        assertThat(result.getTimezone()).isEqualTo("UTC");
        assertThat(result.getHideNsfw()).isFalse();
        verify(entityManager).clear();
    }

    @Test
    @DisplayName("Notification settings lookup returns all supported types")
    void getNotificationSettings_success() {
        User user = User.builder().build();
        UserNotificationSettings notificationSetting =
                new UserNotificationSettings(1L, NotificationType.COMMENT, true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userNotificationSettingsRepository.findByUserIdOrderByModifiedAtDescCreatedAtDesc(1L))
                .thenReturn(List.of(notificationSetting));

        List<NotificationSettingResponse> responses = userSettingsService.getNotificationSettings(1L);

        assertThat(responses).hasSize(NotificationType.values().length);
        assertThat(responses)
                .extracting(NotificationSettingResponse::getNotificationType)
                .containsExactly(NotificationType.LIKE.name(), NotificationType.COMMENT.name(), NotificationType.REPLY.name());
        assertThat(responses)
                .filteredOn(response -> NotificationType.COMMENT.name().equals(response.getNotificationType()))
                .singleElement()
                .extracting(NotificationSettingResponse::isEnabled)
                .isEqualTo(true);
        assertThat(responses)
                .filteredOn(response -> NotificationType.LIKE.name().equals(response.getNotificationType()))
                .singleElement()
                .extracting(NotificationSettingResponse::isEnabled)
                .isEqualTo(true);
    }

    @Test
    @DisplayName("Notification settings lookup tolerates duplicate rows")
    void getNotificationSettings_duplicateRows_keepsFirst() {
        User user = User.builder().build();
        UserNotificationSettings first = new UserNotificationSettings(1L, NotificationType.COMMENT, true);
        UserNotificationSettings duplicate = new UserNotificationSettings(1L, NotificationType.COMMENT, false);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userNotificationSettingsRepository.findByUserIdOrderByModifiedAtDescCreatedAtDesc(1L))
                .thenReturn(List.of(first, duplicate));

        List<NotificationSettingResponse> responses = userSettingsService.getNotificationSettings(1L);

        assertThat(responses)
                .filteredOn(response -> NotificationType.COMMENT.name().equals(response.getNotificationType()))
                .singleElement()
                .extracting(NotificationSettingResponse::isEnabled)
                .isEqualTo(true);
    }

    @Test
    @DisplayName("Settings lookup fails when user does not exist")
    void getSettings_userNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userSettingsService.getSettings(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("Settings update fails when user does not exist")
    void updateSettings_userNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userSettingsService.updateSettings(1L, "LIGHT", "ko", "Asia/Seoul", true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("Settings update fails when user is sanctioned")
    void updateSettings_bannedUser() {
        User user = User.builder().build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(user);

        assertThatThrownBy(() -> userSettingsService.updateSettings(1L, "LIGHT", "ko", "Asia/Seoul", true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);

        verify(userSettingsRepository, never()).save(any());
    }

    @Test
    @DisplayName("Notification settings lookup fails when user does not exist")
    void getNotificationSettings_userNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userSettingsService.getNotificationSettings(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("Bulk notification settings update succeeds")
    void updateNotificationSettings_success() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        UserNotificationSettings likeSetting = new UserNotificationSettings(1L, NotificationType.LIKE, true);
        UserNotificationSettings replySetting = new UserNotificationSettings(1L, NotificationType.REPLY, false);

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userNotificationSettingsRepository.findByUserIdOrderByModifiedAtDescCreatedAtDesc(1L))
                .thenReturn(List.of(likeSetting, replySetting));
        when(userNotificationSettingsRepository.saveAllAndFlush(org.mockito.ArgumentMatchers.<Iterable<UserNotificationSettings>>any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<NotificationSettingResponse> responses = userSettingsService.updateNotificationSettings(1L, List.of(
                new UpdateNotificationSettingItem("like", false),
                new UpdateNotificationSettingItem("comment", false),
                new UpdateNotificationSettingItem("reply", true)));

        assertThat(responses).hasSize(NotificationType.values().length);
        assertThat(responses)
                .filteredOn(response -> NotificationType.LIKE.name().equals(response.getNotificationType()))
                .singleElement()
                .extracting(NotificationSettingResponse::isEnabled)
                .isEqualTo(false);
        assertThat(responses)
                .filteredOn(response -> NotificationType.COMMENT.name().equals(response.getNotificationType()))
                .singleElement()
                .extracting(NotificationSettingResponse::isEnabled)
                .isEqualTo(false);
        assertThat(responses)
                .filteredOn(response -> NotificationType.REPLY.name().equals(response.getNotificationType()))
                .singleElement()
                .extracting(NotificationSettingResponse::isEnabled)
                .isEqualTo(true);

        verify(userNotificationSettingsRepository).saveAllAndFlush(argThat((Iterable<UserNotificationSettings> settings) -> {
            List<UserNotificationSettings> saved = StreamSupport
                    .stream(settings.spliterator(), false)
                    .toList();
            return saved.size() == 3
                    && saved.stream().anyMatch(setting ->
                            setting.getNotificationType() == NotificationType.LIKE && !setting.getIsEnabled())
                    && saved.stream().anyMatch(setting ->
                            setting.getNotificationType() == NotificationType.COMMENT && !setting.getIsEnabled())
                    && saved.stream().anyMatch(setting ->
                            setting.getNotificationType() == NotificationType.REPLY && setting.getIsEnabled());
        }));
        InOrder inOrder = inOrder(userRepository, sanctionService, userNotificationSettingsRepository);
        inOrder.verify(userRepository).findByIdForUpdate(1L);
        inOrder.verify(sanctionService).validateNotBanned(user);
        inOrder.verify(userNotificationSettingsRepository).findByUserIdOrderByModifiedAtDescCreatedAtDesc(1L);
    }

    @Test
    @DisplayName("Bulk notification settings update reloads and reapplies when concurrent insert wins")
    void updateNotificationSettings_duplicateInsert_reloadsAndReapplies() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        UserNotificationSettings reloadedCommentSetting =
                new UserNotificationSettings(1L, NotificationType.COMMENT, true);

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userNotificationSettingsRepository.findByUserIdOrderByModifiedAtDescCreatedAtDesc(1L))
                .thenReturn(List.of())
                .thenReturn(List.of(reloadedCommentSetting));
        when(userNotificationSettingsRepository.saveAllAndFlush(
                org.mockito.ArgumentMatchers.<Iterable<UserNotificationSettings>>any()))
                .thenThrow(new DataIntegrityViolationException("duplicate notification setting"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<NotificationSettingResponse> responses = userSettingsService.updateNotificationSettings(1L, List.of(
                new UpdateNotificationSettingItem("comment", false)));

        assertThat(responses)
                .filteredOn(response -> NotificationType.COMMENT.name().equals(response.getNotificationType()))
                .singleElement()
                .extracting(NotificationSettingResponse::isEnabled)
                .isEqualTo(false);
        assertThat(reloadedCommentSetting.getIsEnabled()).isFalse();
        verify(entityManager).clear();
        verify(userNotificationSettingsRepository, times(2)).saveAllAndFlush(argThat((Iterable<UserNotificationSettings> settings) ->
                StreamSupport.stream(settings.spliterator(), false)
                        .anyMatch(setting -> setting.getNotificationType() == NotificationType.COMMENT
                                && !setting.getIsEnabled())));
    }

    @Test
    @DisplayName("Bulk notification settings update skips saveAll when nothing changes")
    void updateNotificationSettings_noChanges_skipsSaveAll() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        UserNotificationSettings likeSetting = new UserNotificationSettings(1L, NotificationType.LIKE, false);
        UserNotificationSettings replySetting = new UserNotificationSettings(1L, NotificationType.REPLY, true);

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userNotificationSettingsRepository.findByUserIdOrderByModifiedAtDescCreatedAtDesc(1L))
                .thenReturn(List.of(likeSetting, replySetting));

        List<NotificationSettingResponse> responses = userSettingsService.updateNotificationSettings(1L, List.of(
                new UpdateNotificationSettingItem("like", false),
                new UpdateNotificationSettingItem("reply", true)));

        assertThat(responses)
                .filteredOn(response -> NotificationType.LIKE.name().equals(response.getNotificationType()))
                .singleElement()
                .extracting(NotificationSettingResponse::isEnabled)
                .isEqualTo(false);
        assertThat(responses)
                .filteredOn(response -> NotificationType.REPLY.name().equals(response.getNotificationType()))
                .singleElement()
                .extracting(NotificationSettingResponse::isEnabled)
                .isEqualTo(true);
        verify(userNotificationSettingsRepository, never()).saveAllAndFlush(any());
        verify(userRepository).findByIdForUpdate(1L);
    }

    @Test
    @DisplayName("Bulk notification settings update fails for duplicate types")
    void updateNotificationSettings_duplicateType_throwsInvalidInput() {
        User user = User.builder().build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userSettingsService.updateNotificationSettings(1L, List.of(
                new UpdateNotificationSettingItem("like", true),
                new UpdateNotificationSettingItem("LIKE", false))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(userNotificationSettingsRepository, never()).findByUserIdOrderByModifiedAtDescCreatedAtDesc(any());
    }

    @Test
    @DisplayName("Bulk notification settings update fails for null request list")
    void updateNotificationSettings_nullRequests_throwsInvalidInput() {
        assertThatThrownBy(() -> userSettingsService.updateNotificationSettings(1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(userRepository, never()).findByIdForUpdate(any());
        verify(userNotificationSettingsRepository, never()).saveAllAndFlush(any());
    }

    @Test
    @DisplayName("Bulk notification settings update fails for empty request list")
    void updateNotificationSettings_emptyRequests_throwsInvalidInput() {
        assertThatThrownBy(() -> userSettingsService.updateNotificationSettings(1L, List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(userRepository, never()).findByIdForUpdate(any());
        verify(userNotificationSettingsRepository, never()).saveAllAndFlush(any());
    }

    @Test
    @DisplayName("Bulk notification settings update fails for null request item")
    void updateNotificationSettings_nullRequestItem_throwsInvalidInput() {
        assertThatThrownBy(() -> userSettingsService.updateNotificationSettings(1L, Collections.singletonList(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(userRepository, never()).findByIdForUpdate(any());
        verify(userNotificationSettingsRepository, never()).saveAllAndFlush(any());
    }

    @Test
    @DisplayName("Bulk notification settings update fails when user is sanctioned")
    void updateNotificationSettings_bannedUser() {
        User user = User.builder().build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(user);

        assertThatThrownBy(() -> userSettingsService.updateNotificationSettings(1L, List.of(
                new UpdateNotificationSettingItem("like", true))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);

        verify(userNotificationSettingsRepository, never()).saveAllAndFlush(any());
    }
}
