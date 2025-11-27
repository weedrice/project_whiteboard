package com.weedrice.whiteboard.global.common.service;

import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import com.weedrice.whiteboard.global.common.entity.GlobalConfig;
import com.weedrice.whiteboard.global.common.repository.GlobalConfigRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GlobalConfigService {

    private final GlobalConfigRepository globalConfigRepository;

    @Cacheable(value = "globalConfig", key = "#key")
    public String getConfig(String key) {
        return globalConfigRepository.findById(key)
                .map(GlobalConfig::getConfigValue)
                .orElse(null);
    }

    @Transactional
    @CacheEvict(value = "globalConfig", key = "#key")
    public GlobalConfig updateConfig(String key, String value) {
        SecurityUtils.validateSuperAdminPermission();
        GlobalConfig config = globalConfigRepository.findById(key)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        
        config.setConfigValue(value);
        
        return globalConfigRepository.save(config);
    }
}
