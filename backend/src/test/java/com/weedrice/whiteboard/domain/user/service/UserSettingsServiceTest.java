package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.user.dto.NotificationSettingResponse;
import com.weedrice.whiteboard.domain.user.dto.UserSettingsResponse;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettings;
import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettingsId;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.UserNotificationSettingsRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSettingsServiceTest {

    @InjectMocks
    private UserSettingsService userSettingsService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSettingsRepository userSettingsRepository;

    @Mock
    private UserNotificationSettingsRepository userNotificationSettingsRepository;

    @Test
    @DisplayName("설정 조회 성공")
    void getSettings_success() {
        User user = User.builder().build();
        UserSettings settings = new UserSettings(user);
        
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));

        UserSettingsResponse response = userSettingsService.getSettings(1L);
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("설정 수정 성공")
    void updateSettings_success() {
        User user = User.builder().build();
        UserSettings settings = new UserSettings(user);
        
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));
        when(userSettingsRepository.save(any())).thenReturn(settings);

        UserSettingsResponse response = userSettingsService.updateSettings(1L, "dark", "en", "UTC", true);
        
        assertThat(response.getTheme()).isEqualTo("dark");
        assertThat(response.isHideNsfw()).isTrue();
    }

    @Test
    @DisplayName("알림 설정 조회 성공")
    void getNotificationSettings_success() {
        User user = User.builder().build();
        UserNotificationSettings notificationSetting = new UserNotificationSettings(1L, "email", true);
        
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userNotificationSettingsRepository.findByUserId(1L)).thenReturn(List.of(notificationSetting));

        List<NotificationSettingResponse> responses = userSettingsService.getNotificationSettings(1L);
        assertThat(responses).hasSize(1);
    }

    @Test
    @DisplayName("알림 설정 수정 성공")
    void updateNotificationSetting_success() {
        User user = User.builder().build();
        UserNotificationSettings notificationSetting = new UserNotificationSettings(1L, "email", true);
        UserNotificationSettingsId id = new UserNotificationSettingsId(1L, "email");
        
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userNotificationSettingsRepository.findById(id)).thenReturn(Optional.of(notificationSetting));
        when(userNotificationSettingsRepository.save(any())).thenReturn(notificationSetting);

        NotificationSettingResponse response = userSettingsService.updateNotificationSetting(1L, "email", false);
        
        assertThat(response.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("설정 조회 실패 - 사용자 없음")
    void getSettings_userNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userSettingsService.getSettings(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("설정 수정 실패 - 사용자 없음")
    void updateSettings_userNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userSettingsService.updateSettings(1L, "ko", "light", "Asia/Seoul", true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("알림 설정 조회 실패 - 사용자 없음")
    void getNotificationSettings_userNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userSettingsService.getNotificationSettings(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("알림 설정 수정 실패 - 사용자 없음")
    void updateNotificationSetting_userNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userSettingsService.updateNotificationSetting(1L, "email", true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
