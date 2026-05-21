package com.weedrice.whiteboard.global.common.service;

import com.weedrice.whiteboard.global.common.dto.GlobalConfigResponse;
import com.weedrice.whiteboard.global.common.entity.GlobalConfig;
import com.weedrice.whiteboard.global.common.repository.GlobalConfigRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
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
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GlobalConfigService {

    private static final String GLOBAL_CONFIG_CACHE = "globalConfig";
    private static final String POINT_CONFIG_PREFIX = "POINT_";
    private static final int MAX_CONFIG_KEY_LENGTH = 100;
    private static final int MAX_CONFIG_VALUE_LENGTH = 10_000;
    private static final int MAX_CONFIG_DESCRIPTION_LENGTH = 255;

    private final GlobalConfigRepository globalConfigRepository;
    private final CacheManager cacheManager;
    private final GlobalConfigAdminGuard adminGuard;
    private final GlobalConfigDuplicatePolicy duplicatePolicy;

    @Cacheable(value = GLOBAL_CONFIG_CACHE,
            key = "T(com.weedrice.whiteboard.global.common.service.GlobalConfigService).normalizeConfigKey(#key)")
    public String getConfig(String key) {
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

    public GlobalConfigResponse getConfigResponseOrThrow(String key) {
        adminGuard.requireSuperAdmin();
        String normalizedKey = normalizeConfigKey(key);
        String value = getConfigOrThrow(normalizedKey);
        return GlobalConfigResponse.builder()
                .key(normalizedKey)
                .value(value)
                .build();
    }

    public static int parseIntConfigOrDefault(String value, int defaultValue, int minValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsedValue = Integer.parseInt(value.trim());
            return parsedValue >= minValue ? parsedValue : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public List<GlobalConfigResponse> getAllConfigs() {
        adminGuard.requireSuperAdmin();
        return globalConfigRepository.findAll().stream()
                .map(GlobalConfigResponse::from)
                .toList();
    }

    public List<GlobalConfigResponse> getPublicConfigs() {
        // POINT_ 로 시작하는 설정값만 반환
        return globalConfigRepository.findByConfigKeyStartingWith("POINT_").stream()
                .map(GlobalConfigResponse::from)
                .toList();
    }

    @Transactional
    public GlobalConfigResponse createConfig(String key, String value, String description) {
        adminGuard.requireSuperAdmin();
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
    public GlobalConfigResponse updateConfig(String key, String value, String description) {
        adminGuard.requireSuperAdmin();
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
    public void deleteConfig(String key) {
        adminGuard.requireSuperAdmin();
        String normalizedKey = normalizeRequiredText(key, MAX_CONFIG_KEY_LENGTH);
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
        return normalizeNullableText(key);
    }

    private NormalizedConfigInput normalizeConfigInput(String key, String value, String description) {
        return new NormalizedConfigInput(
                normalizeRequiredText(key, MAX_CONFIG_KEY_LENGTH),
                normalizeRequiredText(value, MAX_CONFIG_VALUE_LENGTH),
                normalizeOptionalText(description, MAX_CONFIG_DESCRIPTION_LENGTH));
    }

    private static String normalizeRequiredText(String value, int maxLength) {
        String normalizedValue = normalizeNullableText(value);
        if (!StringUtils.hasText(normalizedValue) || normalizedValue.length() > maxLength) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedValue;
    }

    private static String normalizeOptionalText(String value, int maxLength) {
        String normalizedValue = normalizeNullableText(value);
        if (normalizedValue != null && normalizedValue.length() > maxLength) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedValue;
    }

    private static String normalizeNullableText(String value) {
        return value == null ? null : value.trim();
    }

    private void validateConfigValue(String key, String value) {
        if (key == null || !key.startsWith(POINT_CONFIG_PREFIX)) {
            return;
        }
        try {
            if (Integer.parseInt(value.trim()) < 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
        } catch (NullPointerException | NumberFormatException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private record NormalizedConfigInput(String key, String value, String description) {
    }
}
