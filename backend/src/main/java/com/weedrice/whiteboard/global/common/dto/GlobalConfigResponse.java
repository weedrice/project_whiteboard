package com.weedrice.whiteboard.global.common.dto;

import com.weedrice.whiteboard.global.common.entity.GlobalConfig;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GlobalConfigResponse {

    private String key;
    private String value;
    private String description;

    public static GlobalConfigResponse from(GlobalConfig config) {
        return GlobalConfigResponse.builder()
                .key(config.getConfigKey())
                .value(config.getConfigValue())
                .description(config.getDescription())
                .build();
    }
}
