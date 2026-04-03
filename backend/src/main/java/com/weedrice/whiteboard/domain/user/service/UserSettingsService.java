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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
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
                validateUserExists(userId);

                Map<NotificationType, UserNotificationSettings> settingsByType = userNotificationSettingsRepository
                                .findByUserId(userId).stream()
                                .collect(Collectors.toMap(
                                                UserNotificationSettings::getNotificationType,
                                                setting -> setting,
                                                (existing, duplicate) -> {
                                                        log.warn("Duplicate notification setting detected for userId={} type={}. Keeping the first row.",
                                                                        userId, existing.getNotificationType());
                                                        return existing;
                                                },
                                                LinkedHashMap::new));

                return List.of(NotificationType.values()).stream()
                                .map(type -> {
                                        UserNotificationSettings setting = settingsByType.get(type);
                                        boolean enabled = setting == null || Boolean.TRUE.equals(setting.getIsEnabled());
                                        return new NotificationSettingResponse(type.name(), enabled);
                                })
                                .collect(Collectors.toList());
        }

        @Transactional
        public List<NotificationSettingResponse> updateNotificationSettings(Long userId,
                        List<UpdateNotificationSettingItem> requests) {
                validateUserExists(userId);
                validateNoDuplicateNotificationTypes(requests);

                for (UpdateNotificationSettingItem request : requests) {
                        NotificationType normalizedType = NotificationType.normalize(request.getNotificationType());
                        upsertNotificationSetting(userId, normalizedType, request.getIsEnabled());
                }

                return getNotificationSettings(userId);
        }

        private void validateUserExists(Long userId) {
                userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        }

        private void validateNoDuplicateNotificationTypes(List<UpdateNotificationSettingItem> requests) {
                Set<NotificationType> uniqueTypes = requests.stream()
                                .map(request -> NotificationType.normalize(request.getNotificationType()))
                                .collect(Collectors.toSet());

                if (uniqueTypes.size() != requests.size()) {
                                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
                }
        }

        private UserNotificationSettings upsertNotificationSetting(Long userId, NotificationType notificationType,
                        Boolean isEnabled) {
                UserNotificationSettingsId id = new UserNotificationSettingsId(userId, notificationType);
                UserNotificationSettings setting = userNotificationSettingsRepository.findById(id)
                                .orElse(UserNotificationSettings.builder()
                                                .userId(userId)
                                                .notificationType(notificationType)
                                                .isEnabled(true)
                                                .build());

                setting.setEnabled(Boolean.TRUE.equals(isEnabled));
                return userNotificationSettingsRepository.save(setting);
        }
}
