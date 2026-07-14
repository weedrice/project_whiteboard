package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmoticonImageLimitPolicy {

    public static final String CONFIG_KEY = GlobalConfigService.EMOTICON_IMAGE_MAX_COUNT_CONFIG_KEY;
    public static final int DEFAULT_LIMIT = 20;
    public static final int ABSOLUTE_MAX_LIMIT = GlobalConfigService.EMOTICON_IMAGE_MAX_COUNT_MAX;

    private final GlobalConfigService globalConfigService;

    public int getCurrentLimit() {
        String configuredValue = globalConfigService.getConfigFresh(CONFIG_KEY);
        return GlobalConfigService.parseIntConfigOrDefault(
                configuredValue,
                DEFAULT_LIMIT,
                GlobalConfigService.EMOTICON_IMAGE_MAX_COUNT_MIN,
                ABSOLUTE_MAX_LIMIT);
    }

    public boolean isRequestedCountAllowed(int requestedCount) {
        return requestedCount >= 0 && requestedCount <= getCurrentLimit();
    }
}
