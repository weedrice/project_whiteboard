package com.weedrice.whiteboard.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.cache")
public class CacheFeatureProperties {

    private boolean readOptimizationEnabled = false;
}
