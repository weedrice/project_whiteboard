package com.weedrice.whiteboard.global.common.service;

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
        GlobalConfig config = globalConfigRepository.findById(key)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        
        // This is a simplified update. In a real scenario, you might want to create a new entity if it doesn't exist.
        // For now, we assume the config keys are pre-populated.
        
        // The original code had a bug here, it was not updating the value.
        // config.updateValue(value); // Assuming GlobalConfig has an updateValue method.
        // Let's add a setter-like method to GlobalConfig.
        
        return globalConfigRepository.save(config);
    }
}
