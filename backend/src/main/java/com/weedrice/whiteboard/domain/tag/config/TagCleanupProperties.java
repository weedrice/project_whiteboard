package com.weedrice.whiteboard.domain.tag.config;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "tag.cleanup")
public class TagCleanupProperties {

    @Positive
    private int batchSize = 500;

    @Positive
    private int maxBatches = 10;

    @Positive
    private int maxRetryAttempts = 3;
}
