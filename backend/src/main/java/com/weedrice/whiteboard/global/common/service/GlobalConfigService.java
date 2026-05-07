package com.weedrice.whiteboard.global.common.service;

import com.weedrice.whiteboard.global.common.dto.GlobalConfigResponse;
import com.weedrice.whiteboard.global.common.entity.GlobalConfig;
import com.weedrice.whiteboard.global.common.repository.GlobalConfigRepository;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GlobalConfigService {

    private static final String GLOBAL_CONFIG_CACHE = "globalConfig";
    private static final String POINT_CONFIG_PREFIX = "POINT_";

    private final GlobalConfigRepository globalConfigRepository;
    private final CacheManager cacheManager;

    @Cacheable(value = GLOBAL_CONFIG_CACHE, key = "#key")
    public String getConfig(String key) {
        return globalConfigRepository.findById(key)
                .map(GlobalConfig::getConfigValue)
                .orElse(null);
    }

    public String getConfigOrThrow(String key) {
        return globalConfigRepository.findById(key)
                .map(GlobalConfig::getConfigValue)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
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
        SecurityUtils.validateSuperAdminPermission();
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
        SecurityUtils.validateSuperAdminPermission();
        validateConfigValue(key, value);
        if (globalConfigRepository.existsById(key)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        GlobalConfig config = new GlobalConfig(key, value, description);
        try {
            GlobalConfig savedConfig = globalConfigRepository.saveAndFlush(config);
            putConfigCacheAfterCommit(key, savedConfig.getConfigValue());
            return GlobalConfigResponse.from(savedConfig);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    @Transactional
    @CacheEvict(value = GLOBAL_CONFIG_CACHE, key = "#key")
    public GlobalConfigResponse updateConfig(String key, String value, String description) {
        SecurityUtils.validateSuperAdminPermission();
        validateConfigValue(key, value);
        GlobalConfig config = globalConfigRepository.findById(key)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        config.setConfigValue(value);
        if (description != null) {
            config.setDescription(description);
        }

        return GlobalConfigResponse.from(globalConfigRepository.save(config));
    }

    @Transactional
    @CacheEvict(value = GLOBAL_CONFIG_CACHE, key = "#key")
    public void deleteConfig(String key) {
        SecurityUtils.validateSuperAdminPermission();
        if (!globalConfigRepository.existsById(key)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        globalConfigRepository.deleteById(key);
    }

    private void putConfigCache(String key, String value) {
        Cache cache = cacheManager.getCache(GLOBAL_CONFIG_CACHE);
        if (cache != null) {
            cache.put(key, value);
        }
    }

    private void putConfigCacheAfterCommit(String key, String value) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            putConfigCache(key, value);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    putConfigCache(key, value);
                } catch (RuntimeException ex) {
                    log.warn("Failed to refresh global config cache after commit: key={}", key, ex);
                }
            }
        });
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
}
