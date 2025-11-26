package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSettingsServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSettingsRepository userSettingsRepository;

    @InjectMocks
    private UserSettingsService userSettingsService;

    private User user;
    private UserSettings userSettings;

    @BeforeEach
    void setUp() {
        user = User.builder().build();
        userSettings = UserSettings.builder().user(user).build();
    }

    @Test
    @DisplayName("설정 조회 성공")
    void getSettings_success() {
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
    @DisplayName("설정 수정 성공")
    void updateSettings_success() {
        // given
        Long userId = 1L;
        String newTheme = "DARK";
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(userId)).thenReturn(Optional.of(userSettings));

        // when
        UserSettings updatedSettings = userSettingsService.updateSettings(userId, newTheme, null, null, null);

        // then
        assertThat(updatedSettings.getTheme()).isEqualTo(newTheme);
    }
}
