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
import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
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
import static org.mockito.Mockito.lenient;
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
    private GlobalConfigService globalConfigService;

    @BeforeEach
    void setUp() {
        UserWritableResolver userWritableResolver = new UserWritableResolver(userRepository, sanctionService);
        userSettingsService = new UserSettingsService(
                userSettingsRepository,
                userNotificationSettingsRepository,
                userWritableResolver,
                globalConfigService,
                Clock.fixed(Instant.parse("2026-07-25T01:23:45Z"), DateTimeUtils.KST_ZONE_ID));
        lenient().when(globalConfigService.isInquiryNotificationTypeEnabled()).thenReturn(true);
    }

    @Test
    @DisplayName("Settings lookup succeeds")
    void getSettings_success() {
        when(userSettingsRepository.findSettingsReadByUserId(1L))
                .thenReturn(Optional.of(settingsProjection(1L, "LIGHT", "ko", "Asia/Seoul", true)));

        UserSettingsResponse response = userSettingsService.getSettings(1L);

        assertThat(response).isNotNull();
        verify(userRepository, never()).existsById(any());
    }

    @Test
    @DisplayName("Settings lookup returns defaults when settings row is missing")
    void getSettings_missingRow_returnsDefaults() {
        when(userSettingsRepository.findSettingsReadByUserId(1L))
                .thenReturn(Optional.of(settingsProjection(1L, null, null, null, null)));

        UserSettingsResponse response = userSettingsService.getSettings(1L);

        assertThat(response.getTheme()).isEqualTo("LIGHT");
        assertThat(response.getLanguage()).isEqualTo("ko");
        assertThat(response.getTimezone()).isEqualTo("Asia/Seoul");
        assertThat(response.isHideNsfw()).isTrue();
        verify(userSettingsRepository, never()).save(any());
        verify(userRepository, never()).existsById(any());
    }

    @Test
    @DisplayName("Settings update succeeds")
    void updateSettings_success() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        UserSettings settings = new UserSettings(user);

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));
        when(userSettingsRepository.save(any())).thenReturn(settings);

        UserSettingsResponse response = userSettingsService.updateSettings(1L, " dark ", " EN ", " UTC ", true);

        assertThat(response.getTheme()).isEqualTo("DARK");
        assertThat(response.getLanguage()).isEqualTo("en");
        assertThat(response.getTimezone()).isEqualTo("UTC");
        assertThat(response.isHideNsfw()).isTrue();
        verify(userRepository).findByIdForUpdate(1L);
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
    @DisplayName("Settings update stores the AUTO timezone marker")
    void updateSettings_autoTimezone() {
        // "자동"은 ZoneId로 표현할 수 없는 선택지다. 이 값을 받지 않으면 클라이언트가
        // 자동을 저장할 방법이 없어, 이전에 고른 지역이 계속 남는다.
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        UserSettings settings = new UserSettings(user);
        ReflectionTestUtils.setField(settings, "timezone", "Asia/Tokyo");

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));
        when(userSettingsRepository.save(any())).thenReturn(settings);

        UserSettingsResponse response = userSettingsService.updateSettings(
                1L, null, null, " " + UserSettingsService.AUTO_TIMEZONE + " ", null);

        assertThat(response.getTimezone()).isEqualTo(UserSettingsService.AUTO_TIMEZONE);
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
    @DisplayName("Settings row creation serializes on the user row")
    void getOrCreateSettingsEntity_usesUserWriteLock() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        UserSettings settings = new UserSettings(user);

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));

        UserSettings result = userSettingsService.getOrCreateSettingsEntity(1L);

        assertThat(result).isSameAs(settings);
        verify(userRepository).findByIdForUpdate(1L);
    }

    @Test
    @DisplayName("Settings update creates a missing row while holding the user lock")
    void updateSettingsEntity_createsMissingRowWithUserWriteLock() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        UserSettings settings = new UserSettings(user);

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.empty());
        when(userSettingsRepository.saveAndFlush(any(UserSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userSettingsRepository.save(any(UserSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserSettings result = userSettingsService.updateSettingsEntity(1L, "DARK", "en", "UTC", false);

        assertThat(result.getTheme()).isEqualTo("DARK");
        assertThat(result.getLanguage()).isEqualTo("en");
        assertThat(result.getTimezone()).isEqualTo("UTC");
        assertThat(result.getHideNsfw()).isFalse();
        verify(userRepository).findByIdForUpdate(1L);
    }

    @Test
    @DisplayName("Notification settings lookup returns all supported types")
    void getNotificationSettings_success() {
        when(userNotificationSettingsRepository.findNotificationSettingsReadByUserId(1L))
                .thenReturn(List.of(notificationProjection(1L, NotificationType.COMMENT, true)));

        List<NotificationSettingResponse> responses = userSettingsService.getNotificationSettings(1L);

        assertThat(responses).hasSize(NotificationType.values().length);
        assertThat(responses)
                .extracting(NotificationSettingResponse::getNotificationType)
                .containsExactly(Arrays.stream(NotificationType.values())
                        .map(NotificationType::name)
                        .toArray(String[]::new));
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
        verify(userRepository, never()).existsById(any());
    }

    @Test
    @DisplayName("Notification settings lookup tolerates duplicate rows")
    void getNotificationSettings_duplicateRows_keepsFirst() {
        when(userNotificationSettingsRepository.findNotificationSettingsReadByUserId(1L))
                .thenReturn(List.of(
                        notificationProjection(1L, NotificationType.COMMENT, true),
                        notificationProjection(1L, NotificationType.COMMENT, false)));

        List<NotificationSettingResponse> responses = userSettingsService.getNotificationSettings(1L);

        assertThat(responses)
                .filteredOn(response -> NotificationType.COMMENT.name().equals(response.getNotificationType()))
                .singleElement()
                .extracting(NotificationSettingResponse::isEnabled)
                .isEqualTo(true);
        verify(userRepository, never()).existsById(any());
    }

    @Test
    @DisplayName("Rollback window omits the dedicated inquiry notification setting")
    void getNotificationSettings_inquiryTypeDisabled_omitsInquiry() {
        when(globalConfigService.isInquiryNotificationTypeEnabled()).thenReturn(false);
        when(userNotificationSettingsRepository.findNotificationSettingsReadByUserId(1L))
                .thenReturn(List.of(notificationProjection(1L, NotificationType.COMMENT, true)));

        List<NotificationSettingResponse> responses = userSettingsService.getNotificationSettings(1L);

        assertThat(responses)
                .extracting(NotificationSettingResponse::getNotificationType)
                .doesNotContain(NotificationType.INQUIRY.name())
                .hasSize(NotificationType.values().length - 1);
    }

    @Test
    @DisplayName("Settings lookup fails when user does not exist")
    void getSettings_userNotFound() {
        when(userSettingsRepository.findSettingsReadByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userSettingsService.getSettings(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        verify(userRepository, never()).existsById(any());
    }

    @Test
    @DisplayName("Settings update fails when user does not exist")
    void updateSettings_userNotFound() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userSettingsService.updateSettings(1L, "LIGHT", "ko", "Asia/Seoul", true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("Settings update fails when user is sanctioned")
    void updateSettings_bannedUser() {
        User user = User.builder().build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
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
        when(userNotificationSettingsRepository.findNotificationSettingsReadByUserId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> userSettingsService.getNotificationSettings(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        verify(userRepository, never()).existsById(any());
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
    @DisplayName("Rollback window ignores inquiry setting writes and omits it from the response")
    void updateNotificationSettings_inquiryTypeDisabled_doesNotPersistInquiry() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(globalConfigService.isInquiryNotificationTypeEnabled()).thenReturn(false);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userNotificationSettingsRepository.findByUserIdOrderByModifiedAtDescCreatedAtDesc(1L))
                .thenReturn(List.of());

        List<NotificationSettingResponse> responses = userSettingsService.updateNotificationSettings(1L, List.of(
                new UpdateNotificationSettingItem(NotificationType.INQUIRY.name(), false)));

        assertThat(responses)
                .extracting(NotificationSettingResponse::getNotificationType)
                .doesNotContain(NotificationType.INQUIRY.name())
                .hasSize(NotificationType.values().length - 1);
        verify(userNotificationSettingsRepository, never()).saveAllAndFlush(any());
    }

    @Test
    @DisplayName("Bulk notification settings update propagates unexpected duplicate after user serialization")
    void updateNotificationSettings_duplicateInsert_propagates() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userNotificationSettingsRepository.findByUserIdOrderByModifiedAtDescCreatedAtDesc(1L))
                .thenReturn(List.of());
        when(userNotificationSettingsRepository.saveAllAndFlush(
                org.mockito.ArgumentMatchers.<Iterable<UserNotificationSettings>>any()))
                .thenThrow(new DataIntegrityViolationException("duplicate notification setting"));

        assertThatThrownBy(() -> userSettingsService.updateNotificationSettings(1L, List.of(
                new UpdateNotificationSettingItem("comment", false))))
                .isInstanceOf(DataIntegrityViolationException.class);
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

    private UserSettingsRepository.SettingsReadProjection settingsProjection(
            Long userId,
            String theme,
            String language,
            String timezone,
            Boolean hideNsfw) {
        return new UserSettingsRepository.SettingsReadProjection() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public String getTheme() {
                return theme;
            }

            @Override
            public String getLanguage() {
                return language;
            }

            @Override
            public String getTimezone() {
                return timezone;
            }

            @Override
            public Boolean getHideNsfw() {
                return hideNsfw;
            }

            @Override
            public Boolean getPushEnabled() {
                return false;
            }

            @Override
            public LocalDateTime getOnboardingCompletedAt() {
                return null;
            }
        };
    }

    private UserNotificationSettingsRepository.NotificationSettingReadProjection notificationProjection(
            Long userId,
            NotificationType notificationType,
            Boolean enabled) {
        return new UserNotificationSettingsRepository.NotificationSettingReadProjection() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public NotificationType getNotificationType() {
                return notificationType;
            }

            @Override
            public Boolean getEnabled() {
                return enabled;
            }
        };
    }
}
