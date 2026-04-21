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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
        private static final String DEFAULT_THEME = "LIGHT";
        private static final String DEFAULT_LANGUAGE = "ko";
        private static final String DEFAULT_TIMEZONE = "Asia/Seoul";
        private static final boolean DEFAULT_HIDE_NSFW = true;

        private final UserRepository userRepository;
        private final UserSettingsRepository userSettingsRepository;
        private final UserNotificationSettingsRepository userNotificationSettingsRepository;
        private final SanctionService sanctionService;
        private final EntityManager entityManager;

        public UserSettingsResponse getSettings(Long userId) {
                validateUserExists(userId);
                return userSettingsRepository.findById(userId)
                                .map(this::toResponse)
                                .orElseGet(this::defaultSettingsResponse);
        }

        @Transactional
        public UserSettingsResponse updateSettings(Long userId, String theme, String language, String timezone,
                        Boolean hideNsfw) {
                validateUserCanWrite(userId);
                UserSettings settings = updateSettingsEntity(userId, theme, language, timezone, hideNsfw);
                return new UserSettingsResponse(settings.getTheme(), settings.getLanguage(), settings.getTimezone(),
                                settings.getHideNsfw());
        }

        @Transactional
        public UserSettings getOrCreateSettingsEntity(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                return userSettingsRepository.findById(userId)
                                .orElseGet(() -> createSettingsEntity(user));
        }

        @Transactional
        public UserSettings updateSettingsEntity(Long userId, String theme, String language, String timezone,
                        Boolean hideNsfw) {
                UserSettings settings = getOrCreateSettingsEntity(userId);
                settings.updateSettings(theme, language, timezone, hideNsfw);
                return userSettingsRepository.save(settings);
        }

        public List<NotificationSettingResponse> getNotificationSettings(Long userId) {
                validateUserExists(userId);
                return buildNotificationSettingResponses(loadNotificationSettingsByType(userId));
        }

        @Transactional
        public List<NotificationSettingResponse> updateNotificationSettings(Long userId,
                        List<UpdateNotificationSettingItem> requests) {
                validateUserCanWrite(userId);
                List<NormalizedNotificationSettingRequest> normalizedRequests = normalizeNotificationSettingRequests(requests);
                validateNoDuplicateNotificationTypes(normalizedRequests);

                Map<NotificationType, UserNotificationSettings> settingsByType = loadNotificationSettingsByType(userId);
                List<UserNotificationSettings> settingsToSave = new ArrayList<>();

                for (NormalizedNotificationSettingRequest request : normalizedRequests) {
                        UserNotificationSettings setting = settingsByType.get(request.notificationType());
                        if (setting == null) {
                                setting = UserNotificationSettings.builder()
                                                .userId(userId)
                                                .notificationType(request.notificationType())
                                                .isEnabled(true)
                                                .build();
                                settingsByType.put(request.notificationType(), setting);
                        }

                        boolean enabled = request.enabled();
                        if (Boolean.valueOf(enabled).equals(setting.getIsEnabled())) {
                                continue;
                        }

                        setting.setEnabled(enabled);
                        settingsToSave.add(setting);
                }

                if (!settingsToSave.isEmpty()) {
                        userNotificationSettingsRepository.saveAll(settingsToSave);
                }

                return buildNotificationSettingResponses(settingsByType);
        }

        private void validateUserExists(Long userId) {
                userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        }

        private void validateUserCanWrite(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                sanctionService.validateNotBanned(user);
        }

        private UserSettingsResponse toResponse(UserSettings settings) {
                return new UserSettingsResponse(settings.getTheme(), settings.getLanguage(), settings.getTimezone(),
                                settings.getHideNsfw());
        }

        private UserSettingsResponse defaultSettingsResponse() {
                return new UserSettingsResponse(DEFAULT_THEME, DEFAULT_LANGUAGE, DEFAULT_TIMEZONE, DEFAULT_HIDE_NSFW);
        }

        private List<NormalizedNotificationSettingRequest> normalizeNotificationSettingRequests(
                        List<UpdateNotificationSettingItem> requests) {
                return requests.stream()
                                .map(request -> new NormalizedNotificationSettingRequest(
                                                NotificationType.normalize(request.getNotificationType()),
                                                Boolean.TRUE.equals(request.getIsEnabled())))
                                .toList();
        }

        private void validateNoDuplicateNotificationTypes(List<NormalizedNotificationSettingRequest> requests) {
                Set<NotificationType> uniqueTypes = requests.stream()
                                .map(NormalizedNotificationSettingRequest::notificationType)
                                .collect(Collectors.toSet());

                if (uniqueTypes.size() != requests.size()) {
                                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
                }
        }

        private Map<NotificationType, UserNotificationSettings> loadNotificationSettingsByType(Long userId) {
                return userNotificationSettingsRepository.findByUserIdOrderByModifiedAtDescCreatedAtDesc(userId).stream()
                                .collect(Collectors.toMap(
                                                UserNotificationSettings::getNotificationType,
                                                setting -> setting,
                                                (existing, duplicate) -> {
                                                        log.warn("Duplicate notification setting detected for userId={} type={}. Keeping the most recently updated row.",
                                                                        userId, existing.getNotificationType());
                                                        return existing;
                                                },
                                                LinkedHashMap::new));
        }

        private List<NotificationSettingResponse> buildNotificationSettingResponses(
                        Map<NotificationType, UserNotificationSettings> settingsByType) {
                return List.of(NotificationType.values()).stream()
                                .map(type -> {
                                        UserNotificationSettings setting = settingsByType.get(type);
                                        boolean enabled = setting == null || Boolean.TRUE.equals(setting.getIsEnabled());
                                        return new NotificationSettingResponse(type.name(), enabled);
                                })
                                .collect(Collectors.toList());
        }

        private UserSettings createSettingsEntity(User user) {
                UserSettings settings = UserSettings.builder()
                                .user(user)
                                .build();
                try {
                        return userSettingsRepository.saveAndFlush(settings);
                } catch (DataIntegrityViolationException ex) {
                        entityManager.clear();
                        return userSettingsRepository.findById(user.getUserId())
                                        .orElseThrow(() -> ex);
                }
        }

        private record NormalizedNotificationSettingRequest(NotificationType notificationType, boolean enabled) {
        }
}
