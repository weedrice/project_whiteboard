package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettings;
import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettingsId;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.UserNotificationSettingsRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserSettingsServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSettingsRepository userSettingsRepository;
    @Mock
    private UserNotificationSettingsRepository userNotificationSettingsRepository;

    @InjectMocks
    private UserSettingsService userSettingsService;

    private User user;
    private UserSettings userSettings;

    @BeforeEach
    void setUp() {
        user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        userSettings = UserSettings.builder().user(user).build();
    }

    @Test
    @DisplayName("설정 조회 성공 - 기존 설정 존재")
    void getSettings_success_existing() {
        // given
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(userId)).thenReturn(Optional.of(userSettings));

        // when
        UserSettings foundSettings = userSettingsService.getSettings(userId);

        // then
        assertThat(foundSettings).isEqualTo(userSettings);
    }

    @Test
    @DisplayName("설정 조회 성공 - 새로운 설정 생성")
    void getSettings_success_new() {
        // given
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(userId)).thenReturn(Optional.empty());
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(i -> i.getArgument(0));

        // when
        UserSettings foundSettings = userSettingsService.getSettings(userId);

        // then
        assertThat(foundSettings.getUser()).isEqualTo(user);
        assertThat(foundSettings.getTheme()).isEqualTo("LIGHT"); // Default
        verify(userSettingsRepository).save(any(UserSettings.class));
    }

    @Test
    @DisplayName("설정 조회 실패 - 사용자를 찾을 수 없음")
    void getSettings_fail_userNotFound() {
        // given
        Long userId = 99L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThrows(BusinessException.class, () -> userSettingsService.getSettings(userId));
    }

    @Test
    @DisplayName("설정 수정 성공 - 모든 필드 업데이트")
    void updateSettings_updatesAllFields() {
        // given
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(userId)).thenReturn(Optional.of(userSettings));
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(i -> i.getArgument(0));

        // when
        UserSettings updatedSettings = userSettingsService.updateSettings(userId, "DARK", "en", "UTC", false);

        // then
        assertThat(updatedSettings.getTheme()).isEqualTo("DARK");
        assertThat(updatedSettings.getLanguage()).isEqualTo("en");
        assertThat(updatedSettings.getTimezone()).isEqualTo("UTC");
        assertThat(updatedSettings.getHideNsfw()).isFalse();
    }

    @Test
    @DisplayName("설정 수정 성공 - Null이 아닌 필드만 업데이트")
    void updateSettings_updatesOnlyNonNullFields() {
        // given
        Long userId = 1L;
        String originalTimezone = userSettings.getTimezone();
        Boolean originalHideNsfw = userSettings.getHideNsfw();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(userId)).thenReturn(Optional.of(userSettings));
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(i -> i.getArgument(0));

        // when
        UserSettings updatedSettings = userSettingsService.updateSettings(userId, "DARK", "en", null, null);

        // then
        assertThat(updatedSettings.getTheme()).isEqualTo("DARK");
        assertThat(updatedSettings.getLanguage()).isEqualTo("en");
        assertThat(updatedSettings.getTimezone()).isEqualTo(originalTimezone);
        assertThat(updatedSettings.getHideNsfw()).isEqualTo(originalHideNsfw);
    }

    @Test
    @DisplayName("설정 수정 성공 - 새로운 설정 생성")
    void updateSettings_success_new() {
        // given
        Long userId = 1L;
        String newTheme = "DARK";
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(userId)).thenReturn(Optional.empty());
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UserSettings updatedSettings = userSettingsService.updateSettings(userId, newTheme, "en", "UTC", false);

        // then
        assertThat(updatedSettings.getTheme()).isEqualTo(newTheme);
        assertThat(updatedSettings.getLanguage()).isEqualTo("en");
        
        // Should be called 2 times: 1st for default creation, 2nd for update
        verify(userSettingsRepository, times(2)).save(any(UserSettings.class));
    }

    @Test
    @DisplayName("알림 설정 조회 성공")
    void getNotificationSettings_success() {
        // given
        Long userId = 1L;
        UserNotificationSettings notificationSetting = UserNotificationSettings.builder().userId(userId).notificationType("LIKE").isEnabled(true).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userNotificationSettingsRepository.findByUserId(userId)).thenReturn(Collections.singletonList(notificationSetting));

        // when
        List<UserNotificationSettings> settings = userSettingsService.getNotificationSettings(userId);

        // then
        assertThat(settings).hasSize(1);
        assertThat(settings.get(0).getNotificationType()).isEqualTo("LIKE");
    }

    @Test
    @DisplayName("알림 설정 수정 성공 - 기존 설정 존재")
    void updateNotificationSetting_success_existing() {
        // given
        Long userId = 1L;
        String notificationType = "LIKE";
        UserNotificationSettingsId id = new UserNotificationSettingsId(userId, notificationType);
        UserNotificationSettings setting = UserNotificationSettings.builder().userId(userId).notificationType(notificationType).isEnabled(true).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userNotificationSettingsRepository.findById(id)).thenReturn(Optional.of(setting));
        when(userNotificationSettingsRepository.save(any(UserNotificationSettings.class))).thenAnswer(i -> i.getArgument(0));

        // when
        UserNotificationSettings updatedSetting = userSettingsService.updateNotificationSetting(userId, notificationType, false);

        // then
        assertThat(updatedSetting.getIsEnabled()).isFalse();
    }

    @Test
    @DisplayName("알림 설정 수정 성공 - 새로운 설정 생성")
    void updateNotificationSetting_success_new() {
        // given
        Long userId = 1L;
        String notificationType = "COMMENT";
        UserNotificationSettingsId id = new UserNotificationSettingsId(userId, notificationType);
        // New setting is created with default enabled=true, then updated to false
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userNotificationSettingsRepository.findById(id)).thenReturn(Optional.empty());
        when(userNotificationSettingsRepository.save(any(UserNotificationSettings.class))).thenAnswer(i -> i.getArgument(0));

        // when
        UserNotificationSettings updatedSetting = userSettingsService.updateNotificationSetting(userId, notificationType, false);

        // then
        assertThat(updatedSetting.getIsEnabled()).isFalse();
        verify(userNotificationSettingsRepository).save(any(UserNotificationSettings.class));
    }

    @Test
    @DisplayName("알림 설정 수정 - isEnabled가 null일 때 변경 없음")
    void updateNotificationSetting_doesNotUpdateIfIsEnabledIsNull() {
        // given
        Long userId = 1L;
        String notificationType = "LIKE";
        UserNotificationSettingsId id = new UserNotificationSettingsId(userId, notificationType);
        UserNotificationSettings setting = UserNotificationSettings.builder().userId(userId).notificationType(notificationType).isEnabled(true).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userNotificationSettingsRepository.findById(id)).thenReturn(Optional.of(setting));
        when(userNotificationSettingsRepository.save(any(UserNotificationSettings.class))).thenAnswer(i -> i.getArgument(0));

        // when
        userSettingsService.updateNotificationSetting(userId, notificationType, null);

        // then
        ArgumentCaptor<UserNotificationSettings> captor = ArgumentCaptor.forClass(UserNotificationSettings.class);
        verify(userNotificationSettingsRepository).save(captor.capture());
        
        assertThat(captor.getValue().getIsEnabled()).isTrue(); // Should remain true
    }
}