package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.user.dto.NotificationSettingResponse;
import com.weedrice.whiteboard.domain.user.dto.UpdateNotificationSettingItem;
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
import java.util.Map;
import java.util.function.Function;
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

                Map<NotificationType, UserNotificationSettings> settingsByType = userNotificationSettingsRepository
                                .findByUserId(userId).stream()
                                .collect(Collectors.toMap(UserNotificationSettings::getNotificationType, Function.identity()));

                return List.of(NotificationType.values()).stream()
                                .map(type -> {
                                        UserNotificationSettings setting = settingsByType.get(type);
                                        boolean enabled = setting == null || Boolean.TRUE.equals(setting.getIsEnabled());
                                        return new NotificationSettingResponse(type.name(), enabled);
                                })
                                .collect(Collectors.toList());
        }

        @Transactional
        public NotificationSettingResponse updateNotificationSetting(Long userId, String notificationType,
                        Boolean isEnabled) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                NotificationType normalizedType = NotificationType.normalize(notificationType);

                UserNotificationSettingsId id = new UserNotificationSettingsId(userId, normalizedType);

                UserNotificationSettings setting = userNotificationSettingsRepository.findById(id)
                                .orElse(UserNotificationSettings.builder()
                                                .userId(userId)
                                                .notificationType(normalizedType)
                                                .isEnabled(true)
                                                .build());

                if (isEnabled != null) {
                        setting.setEnabled(isEnabled);
                }

                userNotificationSettingsRepository.save(setting);
                return new NotificationSettingResponse(setting.getNotificationType().name(), setting.getIsEnabled());
        }

        @Transactional
        public List<NotificationSettingResponse> updateNotificationSettings(Long userId,
                        List<UpdateNotificationSettingItem> requests) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                for (UpdateNotificationSettingItem request : requests) {
                        NotificationType normalizedType = NotificationType.normalize(request.getNotificationType());
                        UserNotificationSettingsId id = new UserNotificationSettingsId(userId, normalizedType);

                        UserNotificationSettings setting = userNotificationSettingsRepository.findById(id)
                                        .orElse(UserNotificationSettings.builder()
                                                        .userId(userId)
                                                        .notificationType(normalizedType)
                                                        .isEnabled(true)
                                                        .build());

                        setting.setEnabled(Boolean.TRUE.equals(request.getIsEnabled()));
                        userNotificationSettingsRepository.save(setting);
                }

                return getNotificationSettings(user.getUserId());
        }
}
