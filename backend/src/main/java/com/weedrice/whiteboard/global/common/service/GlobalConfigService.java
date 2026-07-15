package com.weedrice.whiteboard.global.common.service;

import com.weedrice.whiteboard.global.common.dto.GlobalConfigResponse;
import com.weedrice.whiteboard.global.common.entity.GlobalConfig;
import com.weedrice.whiteboard.global.common.repository.GlobalConfigRepository;
import com.weedrice.whiteboard.global.common.util.TextInputNormalizer;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.SuperAdminPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GlobalConfigService {

    private static final String GLOBAL_CONFIG_CACHE = "globalConfig";
    private static final String POINT_CONFIG_PREFIX = "POINT_";
    public static final String POINT_BOARD_CREATE_COST_CONFIG_KEY = "POINT_BOARD_CREATE_COST";
    public static final String NOBICON_PRICE_CONFIG_KEY = "NOBICON_PRICE";
    public static final String EMOTICON_IMAGE_MAX_COUNT_CONFIG_KEY = "EMOTICON_IMAGE_MAX_COUNT";
    public static final String REPORT_AUTO_BLIND_THRESHOLD_CONFIG_KEY = "REPORT_AUTO_BLIND_THRESHOLD";
    public static final String AGENT_RULES_VERSION_CONFIG_KEY = "AGENT_RULES_VERSION";
    public static final int MAX_POINT_CONFIG_VALUE = 1_000_000;
    public static final int EMOTICON_IMAGE_MAX_COUNT_MIN = 1;
    public static final int EMOTICON_IMAGE_MAX_COUNT_MAX = 100;
    public static final int AGENT_RULES_VERSION_MAX_LENGTH = 100;
    private static final List<String> PUBLIC_CONFIG_KEYS = List.of(
            POINT_BOARD_CREATE_COST_CONFIG_KEY,
            EMOTICON_IMAGE_MAX_COUNT_CONFIG_KEY);
    private static final int MAX_CONFIG_KEY_LENGTH = 100;
    private static final int MAX_CONFIG_VALUE_LENGTH = 10_000;
    private static final int MAX_CONFIG_DESCRIPTION_LENGTH = 255;

    private final GlobalConfigRepository globalConfigRepository;
    private final CacheManager cacheManager;
    private final SuperAdminPolicy superAdminPolicy;
    private final GlobalConfigDuplicatePolicy duplicatePolicy;

    @Cacheable(value = GLOBAL_CONFIG_CACHE,
            key = "T(com.weedrice.whiteboard.global.common.service.GlobalConfigService).normalizeConfigKey(#key)")
    public String getConfig(String key) {
        String normalizedKey = normalizeConfigKey(key);
        return globalConfigRepository.findById(normalizedKey)
                .map(GlobalConfig::getConfigValue)
                .orElse(null);
    }

    /**
     * Reads the latest persisted value without using the local application cache.
     */
    public String getConfigFresh(String key) {
        String normalizedKey = normalizeConfigKey(key);
        return globalConfigRepository.findById(normalizedKey)
                .map(GlobalConfig::getConfigValue)
                .orElse(null);
    }

    public String getConfigOrThrow(String key) {
        String normalizedKey = normalizeConfigKey(key);
        return globalConfigRepository.findById(normalizedKey)
                .map(GlobalConfig::getConfigValue)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public GlobalConfigResponse getConfigResponseOrThrow(Long actorUserId, String key) {
        superAdminPolicy.requireUsableSuperAdmin(actorUserId);
        String normalizedKey = normalizeConfigKey(key);
        String value = getConfigOrThrow(normalizedKey);
        return GlobalConfigResponse.builder()
                .key(normalizedKey)
                .value(value)
                .build();
    }

    public static int parseIntConfigOrDefault(String value, int defaultValue, int minValue) {
        Integer parsedValue = parseIntConfigValue(value);
        return parsedValue != null && parsedValue >= minValue ? parsedValue : defaultValue;
    }

    public static int parseIntConfigOrDefault(String value, int defaultValue, int minValue, int maxValue) {
        Integer parsedValue = parseIntConfigValue(value);
        return parsedValue != null && parsedValue >= minValue && parsedValue <= maxValue
                ? parsedValue
                : defaultValue;
    }

    public List<GlobalConfigResponse> getAllConfigs(Long actorUserId) {
        superAdminPolicy.requireUsableSuperAdmin(actorUserId);
        return globalConfigRepository.findAll().stream()
                .map(GlobalConfigResponse::from)
                .toList();
    }

    public List<GlobalConfigResponse> getPublicConfigs() {
        Map<String, GlobalConfig> publicConfigs = new LinkedHashMap<>();
        globalConfigRepository.findAllById(PUBLIC_CONFIG_KEYS)
                .forEach(config -> publicConfigs.put(config.getConfigKey(), config));

        return PUBLIC_CONFIG_KEYS.stream()
                .map(publicConfigs::get)
                .filter(Objects::nonNull)
                .map(GlobalConfigResponse::from)
                .toList();
    }

    @Transactional
    public GlobalConfigResponse createConfig(Long actorUserId, String key, String value, String description) {
        superAdminPolicy.requireUsableSuperAdmin(actorUserId);
        NormalizedConfigInput input = normalizeConfigInput(key, value, description);
        validateConfigValue(input.key(), input.value());
        duplicatePolicy.validateCreatable(input.key());
        GlobalConfig config = new GlobalConfig(input.key(), input.value(), input.description());
        try {
            GlobalConfig savedConfig = globalConfigRepository.saveAndFlush(config);
            putConfigCacheAfterCommit(input.key(), savedConfig.getConfigValue());
            return GlobalConfigResponse.from(savedConfig);
        } catch (DataIntegrityViolationException e) {
            throw duplicatePolicy.duplicateKey();
        }
    }

    @Transactional
    public GlobalConfigResponse updateConfig(Long actorUserId, String key, String value, String description) {
        superAdminPolicy.requireUsableSuperAdmin(actorUserId);
        NormalizedConfigInput input = normalizeConfigInput(key, value, description);
        validateConfigValue(input.key(), input.value());
        GlobalConfig config = globalConfigRepository.findById(input.key())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        config.setConfigValue(input.value());
        if (description != null) {
            config.setDescription(input.description());
        }

        GlobalConfig savedConfig = globalConfigRepository.save(config);
        putConfigCacheAfterCommit(input.key(), savedConfig.getConfigValue());
        return GlobalConfigResponse.from(savedConfig);
    }

    @Transactional
    public void deleteConfig(Long actorUserId, String key) {
        superAdminPolicy.requireUsableSuperAdmin(actorUserId);
        String normalizedKey = TextInputNormalizer.normalizeRequired(key, MAX_CONFIG_KEY_LENGTH);
        if (!globalConfigRepository.existsById(normalizedKey)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        globalConfigRepository.deleteById(normalizedKey);
        evictConfigCacheAfterCommit(normalizedKey);
    }

    private void putConfigCache(String key, String value) {
        Cache cache = cacheManager.getCache(GLOBAL_CONFIG_CACHE);
        if (cache != null) {
            cache.put(key, value);
        }
    }

    private void putConfigCacheAfterCommit(String key, String value) {
        runConfigCacheActionAfterCommit(key, "refresh", () -> putConfigCache(key, value));
    }

    private void evictConfigCache(String key) {
        Cache cache = cacheManager.getCache(GLOBAL_CONFIG_CACHE);
        if (cache != null) {
            cache.evict(key);
        }
    }

    private void evictConfigCacheAfterCommit(String key) {
        runConfigCacheActionAfterCommit(key, "evict", () -> evictConfigCache(key));
    }

    private void runConfigCacheActionAfterCommit(String key, String action, Runnable cacheAction) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            cacheAction.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    cacheAction.run();
                } catch (RuntimeException ex) {
                    log.warn("Failed to {} global config cache after commit: key={}", action, key, ex);
                }
            }
        });
    }

    public static String normalizeConfigKey(String key) {
        return TextInputNormalizer.normalizeNullable(key);
    }

    private NormalizedConfigInput normalizeConfigInput(String key, String value, String description) {
        return new NormalizedConfigInput(
                TextInputNormalizer.normalizeRequired(key, MAX_CONFIG_KEY_LENGTH),
                TextInputNormalizer.normalizeRequired(value, MAX_CONFIG_VALUE_LENGTH),
                TextInputNormalizer.normalizeOptional(description, MAX_CONFIG_DESCRIPTION_LENGTH));
    }

    private void validateConfigValue(String key, String value) {
        if (NOBICON_PRICE_CONFIG_KEY.equals(key)) {
            validateIntegerRange(value, 0, MAX_POINT_CONFIG_VALUE);
            return;
        }
        if (EMOTICON_IMAGE_MAX_COUNT_CONFIG_KEY.equals(key)) {
            validateIntegerRange(value, EMOTICON_IMAGE_MAX_COUNT_MIN, EMOTICON_IMAGE_MAX_COUNT_MAX);
            return;
        }
        if (REPORT_AUTO_BLIND_THRESHOLD_CONFIG_KEY.equals(key)) {
            validateIntegerRange(value, 1, Integer.MAX_VALUE);
            return;
        }
        if (AGENT_RULES_VERSION_CONFIG_KEY.equals(key)) {
            if (value.length() > AGENT_RULES_VERSION_MAX_LENGTH) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            return;
        }
        if (key == null || !key.startsWith(POINT_CONFIG_PREFIX)) {
            return;
        }
        validateIntegerRange(value, 0, MAX_POINT_CONFIG_VALUE);
    }

    private void validateIntegerRange(String value, int minValue, int maxValue) {
        Integer parsedValue = parseIntConfigValue(value);
        if (parsedValue == null || parsedValue < minValue || parsedValue > maxValue) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private static Integer parseIntConfigValue(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record NormalizedConfigInput(String key, String value, String description) {
    }
}
