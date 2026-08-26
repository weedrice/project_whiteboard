package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.user.dto.NotificationSettingResponse;
import com.weedrice.whiteboard.domain.user.dto.UpdateNotificationSettingItem;
import com.weedrice.whiteboard.domain.user.dto.UserSettingsResponse;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettings;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.UserNotificationSettingsRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.DateTimeException;
import java.time.Clock;
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
        /**
         * "표시 지역을 브라우저 판단에 맡긴다"를 뜻하는 값.
         *
         * <p>`ZoneId`로 표현할 수 없는 선택지라 별도 표식이 필요하다. 이 값을 받지 않으면
         * 클라이언트가 "자동"을 저장할 방법이 없어, 이전에 고른 지역이 계속 남는다.
         *
         * <p>서버는 이 필드를 판정에 쓰지 않고 저장·응답만 하므로(출석의 "오늘", 일일 한도
         * 등은 모두 KST 고정) 표식을 그대로 담아도 서버 로직에 영향이 없다. 표시 계층만
         * 이 값을 해석한다.
         */
        public static final String AUTO_TIMEZONE = "AUTO";

        private static final Set<String> SUPPORTED_THEMES = Set.of("LIGHT", "DARK");
        private static final Set<String> SUPPORTED_LANGUAGES = Set.of("ko", "en");

        private final UserSettingsRepository userSettingsRepository;
        private final UserNotificationSettingsRepository userNotificationSettingsRepository;
        private final UserWritableResolver userWritableResolver;
        private final GlobalConfigService globalConfigService;
        private final Clock clock;

        public UserSettingsResponse getSettings(Long userId) {
                UserSettingsRepository.SettingsReadProjection settings = userSettingsRepository
                                .findSettingsReadByUserId(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                return toReadResponse(settings);
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
                                settings.getHideNsfw(), Boolean.TRUE.equals(settings.getPushEnabled()),
                                settings.getOnboardingCompletedAt());
        }

        @Transactional
        public UserSettingsResponse setPushEnabled(Long userId, boolean pushEnabled) {
                User user = validateUserCanWrite(userId);
                UserSettings settings = getOrCreateSettingsEntity(user);
                settings.setPushEnabled(pushEnabled);
                return toResponse(userSettingsRepository.save(settings));
        }

        @Transactional
        public UserSettingsResponse setPushEnabledForLockedUser(User user, boolean pushEnabled) {
                UserSettings settings = getOrCreateSettingsEntity(user);
                settings.setPushEnabled(pushEnabled);
                return toResponse(userSettingsRepository.save(settings));
        }

        @Transactional
        public UserSettingsResponse completeOnboarding(Long userId) {
                User user = validateUserCanWrite(userId);
                UserSettings settings = getOrCreateSettingsEntity(user);
                settings.completeOnboarding(LocalDateTime.now(clock));
                return toResponse(userSettingsRepository.save(settings));
        }

        @Transactional
        public UserSettings getOrCreateSettingsEntity(Long userId) {
                return getOrCreateSettingsEntity(userWritableResolver.resolveForUpdate(userId));
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
                return buildNotificationSettingResponsesFromStates(loadNotificationSettingStatesByType(userId));
        }

        @Transactional
        public List<NotificationSettingResponse> updateNotificationSettings(Long userId,
                        List<UpdateNotificationSettingItem> requests) {
                validateNotificationSettingRequests(requests);
                userWritableResolver.resolveForUpdate(userId);
                List<NormalizedNotificationSettingRequest> normalizedRequests = normalizeNotificationSettingRequests(requests);
                validateNoDuplicateNotificationTypes(normalizedRequests);
                normalizedRequests = retainWritableNotificationTypes(normalizedRequests);

                Map<NotificationType, UserNotificationSettings> settingsByType = loadNotificationSettingsByType(userId);
                List<UserNotificationSettings> settingsToSave = applyNotificationSettingRequests(
                                userId,
                                normalizedRequests,
                                settingsByType);

                if (!settingsToSave.isEmpty()) {
                        userNotificationSettingsRepository.saveAllAndFlush(settingsToSave);
                }

                return buildNotificationSettingResponses(settingsByType);
        }

        private List<UserNotificationSettings> applyNotificationSettingRequests(Long userId,
                        List<NormalizedNotificationSettingRequest> normalizedRequests,
                        Map<NotificationType, UserNotificationSettings> settingsByType) {
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
                return settingsToSave;
        }

        private User validateUserCanWrite(Long userId) {
                return userWritableResolver.resolveForUpdate(userId);
        }

        private UserSettingsResponse toReadResponse(UserSettingsRepository.SettingsReadProjection settings) {
                if (settings.getTheme() == null) {
                        return defaultSettingsResponse();
                }
                return new UserSettingsResponse(
                                settings.getTheme(),
                                settings.getLanguage(),
                                settings.getTimezone(),
                                Boolean.TRUE.equals(settings.getHideNsfw()),
                                Boolean.TRUE.equals(settings.getPushEnabled()),
                                settings.getOnboardingCompletedAt());
        }

        private UserSettingsResponse defaultSettingsResponse() {
                return new UserSettingsResponse(
                                UserSettingsDefaults.THEME,
                                UserSettingsDefaults.LANGUAGE,
                                UserSettingsDefaults.TIMEZONE,
                                UserSettingsDefaults.HIDE_NSFW,
                                UserSettingsDefaults.PUSH_ENABLED,
                                null);
        }

        private UserSettingsResponse toResponse(UserSettings settings) {
                return new UserSettingsResponse(
                                settings.getTheme(),
                                settings.getLanguage(),
                                settings.getTimezone(),
                                Boolean.TRUE.equals(settings.getHideNsfw()),
                                Boolean.TRUE.equals(settings.getPushEnabled()),
                                settings.getOnboardingCompletedAt());
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
                if (AUTO_TIMEZONE.equals(normalizedTimezone)) {
                        return AUTO_TIMEZONE;
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
                if (requests == null || requests.isEmpty()
                                || requests.size() > NotificationType.SUPPORTED_TYPE_COUNT
                                || requests.stream().anyMatch(Objects::isNull)) {
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

        private List<NormalizedNotificationSettingRequest> retainWritableNotificationTypes(
                        List<NormalizedNotificationSettingRequest> requests) {
                if (globalConfigService.isInquiryNotificationTypeEnabled()) {
                        return requests;
                }
                return requests.stream()
                                .filter(request -> request.notificationType() != NotificationType.INQUIRY)
                                .toList();
        }

        private List<NotificationType> readableNotificationTypes() {
                return List.of(NotificationType.values()).stream()
                                .filter(type -> type != NotificationType.INQUIRY
                                                || globalConfigService.isInquiryNotificationTypeEnabled())
                                .toList();
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

        private Map<NotificationType, Boolean> loadNotificationSettingStatesByType(Long userId) {
                List<UserNotificationSettingsRepository.NotificationSettingReadProjection> settings =
                                userNotificationSettingsRepository.findNotificationSettingsReadByUserId(userId);
                if (settings.isEmpty()) {
                        throw new BusinessException(ErrorCode.USER_NOT_FOUND);
                }

                Map<NotificationType, Boolean> settingsByType = new LinkedHashMap<>();
                for (UserNotificationSettingsRepository.NotificationSettingReadProjection setting : settings) {
                        NotificationType notificationType = setting.getNotificationType();
                        if (notificationType == null) {
                                continue;
                        }
                        if (settingsByType.containsKey(notificationType)) {
                                log.warn("Duplicate notification setting detected for userId={} type={}. Keeping the most recently updated row.",
                                                userId, notificationType);
                                continue;
                        }
                        settingsByType.put(notificationType, Boolean.TRUE.equals(setting.getEnabled()));
                }
                return settingsByType;
        }

        private List<NotificationSettingResponse> buildNotificationSettingResponsesFromStates(
                        Map<NotificationType, Boolean> settingsByType) {
                return readableNotificationTypes().stream()
                                .map(type -> {
                                        Boolean enabled = settingsByType.get(type);
                                        return new NotificationSettingResponse(type.name(), enabled == null || enabled);
                                })
                                .collect(Collectors.toList());
        }

        private List<NotificationSettingResponse> buildNotificationSettingResponses(
                        Map<NotificationType, UserNotificationSettings> settingsByType) {
                Map<NotificationType, Boolean> settingStatesByType = settingsByType.entrySet().stream()
                                .collect(Collectors.toMap(
                                                Map.Entry::getKey,
                                                entry -> Boolean.TRUE.equals(entry.getValue().getIsEnabled()),
                                                (existing, duplicate) -> existing,
                                                LinkedHashMap::new));
                return buildNotificationSettingResponsesFromStates(settingStatesByType);
        }

        private UserSettings createSettingsEntity(User user) {
                UserSettings settings = UserSettings.builder()
                                .user(user)
                                .build();
                return userSettingsRepository.saveAndFlush(settings);
        }

        private record NormalizedNotificationSettingRequest(NotificationType notificationType, boolean enabled) {
        }
}
