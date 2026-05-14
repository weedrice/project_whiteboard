package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
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

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
        private static final Set<String> SUPPORTED_THEMES = Set.of("LIGHT", "DARK");
        private static final Set<String> SUPPORTED_LANGUAGES = Set.of("ko", "en");

        private final UserRepository userRepository;
        private final UserSettingsRepository userSettingsRepository;
        private final UserNotificationSettingsRepository userNotificationSettingsRepository;
        private final EntityManager entityManager;
        private final UserWritableResolver userWritableResolver;

        public UserSettingsResponse getSettings(Long userId) {
                validateUserExists(userId);
                return userSettingsRepository.findById(userId)
                                .map(this::toResponse)
                                .orElseGet(this::defaultSettingsResponse);
        }

        @Transactional
        public UserSettingsResponse updateSettings(Long userId, String theme, String language, String timezone,
                        Boolean hideNsfw) {
                String normalizedTheme = normalizeTheme(theme);
                String normalizedLanguage = normalizeLanguage(language);
                String normalizedTimezone = normalizeTimezone(timezone);
                User user = validateUserCanWrite(userId);
                UserSettings settings = updateSettingsEntity(
                                user,
                                normalizedTheme,
                                normalizedLanguage,
                                normalizedTimezone,
                                hideNsfw);
                return new UserSettingsResponse(settings.getTheme(), settings.getLanguage(), settings.getTimezone(),
                                settings.getHideNsfw());
        }

        @Transactional
        public UserSettings getOrCreateSettingsEntity(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                return getOrCreateSettingsEntity(user);
        }

        private UserSettings getOrCreateSettingsEntity(User user) {
                return userSettingsRepository.findById(user.getUserId())
                                .orElseGet(() -> createSettingsEntity(user));
        }

        @Transactional
        public UserSettings updateSettingsEntity(Long userId, String theme, String language, String timezone,
                        Boolean hideNsfw) {
                UserSettings settings = getOrCreateSettingsEntity(userId);
                settings.updateSettings(theme, language, timezone, hideNsfw);
                return userSettingsRepository.save(settings);
        }

        private UserSettings updateSettingsEntity(User user, String theme, String language, String timezone,
                        Boolean hideNsfw) {
                UserSettings settings = getOrCreateSettingsEntity(user);
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
                validateNotificationSettingRequests(requests);
                userWritableResolver.resolveForUpdate(userId);
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

        private User validateUserCanWrite(Long userId) {
                return userWritableResolver.resolve(userId);
        }

        private UserSettingsResponse toResponse(UserSettings settings) {
                return new UserSettingsResponse(settings.getTheme(), settings.getLanguage(), settings.getTimezone(),
                                settings.getHideNsfw());
        }

        private UserSettingsResponse defaultSettingsResponse() {
                return new UserSettingsResponse(DEFAULT_THEME, DEFAULT_LANGUAGE, DEFAULT_TIMEZONE, DEFAULT_HIDE_NSFW);
        }

        private String normalizeTheme(String theme) {
                if (theme == null) {
                        return null;
                }

                String normalizedTheme = theme.strip().toUpperCase(Locale.ROOT);
                if (normalizedTheme.isBlank() || !SUPPORTED_THEMES.contains(normalizedTheme)) {
                        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
                }
                return normalizedTheme;
        }

        private String normalizeLanguage(String language) {
                if (language == null) {
                        return null;
                }

                String normalizedLanguage = language.strip().toLowerCase(Locale.ROOT);
                if (normalizedLanguage.isBlank() || !SUPPORTED_LANGUAGES.contains(normalizedLanguage)) {
                        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
                }
                return normalizedLanguage;
        }

        private String normalizeTimezone(String timezone) {
                if (timezone == null) {
                        return null;
                }

                String normalizedTimezone = timezone.strip();
                if (normalizedTimezone.isBlank()) {
                        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
                }

                try {
                        return ZoneId.of(normalizedTimezone).getId();
                } catch (DateTimeException ex) {
                        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
                }
        }

        private List<NormalizedNotificationSettingRequest> normalizeNotificationSettingRequests(
                        List<UpdateNotificationSettingItem> requests) {
                return requests.stream()
                                .map(request -> new NormalizedNotificationSettingRequest(
                                                NotificationType.normalize(request.getNotificationType()),
                                                Boolean.TRUE.equals(request.getIsEnabled())))
                                .toList();
        }

        private void validateNotificationSettingRequests(List<UpdateNotificationSettingItem> requests) {
                if (requests == null || requests.stream().anyMatch(Objects::isNull)) {
                        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
                }
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
