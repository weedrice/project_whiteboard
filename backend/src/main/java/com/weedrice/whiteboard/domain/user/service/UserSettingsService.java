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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSettingsService {

        private final UserRepository userRepository;
        private final UserSettingsRepository userSettingsRepository;
        private final UserNotificationSettingsRepository userNotificationSettingsRepository;

        @Transactional
        public UserSettingsResponse getSettings(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                UserSettings settings = userSettingsRepository.findById(userId)
                                .orElseGet(() -> {
                                        UserSettings defaultSettings = UserSettings.builder()
                                                        .user(user)
                                                        .build();
                                        return userSettingsRepository.save(defaultSettings);
                                });
                return new UserSettingsResponse(settings.getTheme(), settings.getLanguage(), settings.getTimezone(),
                                settings.getHideNsfw());
        }

        @Transactional
        public UserSettingsResponse updateSettings(Long userId, String theme, String language, String timezone,
                        Boolean hideNsfw) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                UserSettings settings = userSettingsRepository.findById(userId)
                                .orElseGet(() -> {
                                        UserSettings defaultSettings = UserSettings.builder()
                                                        .user(user)
                                                        .build();
                                        return userSettingsRepository.save(defaultSettings);
                                });

                settings.updateSettings(theme, language, timezone, hideNsfw);
                userSettingsRepository.save(settings);

                return new UserSettingsResponse(settings.getTheme(), settings.getLanguage(), settings.getTimezone(),
                                settings.getHideNsfw());
        }

        public List<NotificationSettingResponse> getNotificationSettings(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                return userNotificationSettingsRepository.findByUserId(userId).stream()
                                .map(s -> new NotificationSettingResponse(s.getNotificationType(), s.getIsEnabled()))
                                .collect(Collectors.toList());
        }

        @Transactional
        public NotificationSettingResponse updateNotificationSetting(Long userId, String notificationType,
                        Boolean isEnabled) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                UserNotificationSettingsId id = new UserNotificationSettingsId(userId, notificationType);

                UserNotificationSettings setting = userNotificationSettingsRepository.findById(id)
                                .orElse(UserNotificationSettings.builder()
                                                .userId(userId)
                                                .notificationType(notificationType)
                                                .isEnabled(true)
                                                .build());

                if (isEnabled != null) {
                        setting.setEnabled(isEnabled);
                }

                userNotificationSettingsRepository.save(setting);
                return new NotificationSettingResponse(setting.getNotificationType(), setting.getIsEnabled());
        }
}