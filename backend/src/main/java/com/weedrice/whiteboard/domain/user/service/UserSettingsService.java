package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettings;
import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettingsId;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.UserNotificationSettingsRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSettingsService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserNotificationSettingsRepository userNotificationSettingsRepository;

    public UserSettings getSettings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return userSettingsRepository.findById(userId)
                .orElseGet(() -> {
                    UserSettings defaultSettings = UserSettings.builder()
                            .user(user)
                            .build();
                    return userSettingsRepository.save(defaultSettings);
                });
    }

    @Transactional
    public UserSettings updateSettings(Long userId, String theme, String language, String timezone, Boolean hideNsfw) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        UserSettings settings = userSettingsRepository.findById(userId)
                .orElseGet(() -> {
                    UserSettings defaultSettings = UserSettings.builder()
                            .user(user)
                            .build();
                    return userSettingsRepository.save(defaultSettings);
                });

        String hideNsfwValue = hideNsfw != null ? (hideNsfw ? "Y" : "N") : null;

        settings.updateSettings(theme, language, timezone, hideNsfwValue);

        return settings;
    }

    public List<UserNotificationSettings> getNotificationSettings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return userNotificationSettingsRepository.findByUserId(userId);
    }

    @Transactional
    public UserNotificationSettings updateNotificationSetting(Long userId, String notificationType, Boolean isEnabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        UserNotificationSettingsId id = new UserNotificationSettingsId(userId, notificationType);

        UserNotificationSettings setting = userNotificationSettingsRepository.findById(id)
                .orElse(UserNotificationSettings.builder()
                        .userId(userId)
                        .notificationType(notificationType)
                        .isEnabled("Y")
                        .build());

        if (isEnabled != null) {
            setting.setEnabled(isEnabled);
        }

        return userNotificationSettingsRepository.save(setting);
    }
}