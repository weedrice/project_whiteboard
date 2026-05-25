package com.weedrice.whiteboard.global.common.service;

import com.weedrice.whiteboard.global.common.repository.GlobalConfigRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class GlobalConfigDuplicatePolicy {

    private final GlobalConfigRepository globalConfigRepository;

    void validateCreatable(String key) {
        if (globalConfigRepository.existsById(key)) {
            throw duplicateKey();
        }
    }

    BusinessException duplicateKey() {
        return new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
    }
}
