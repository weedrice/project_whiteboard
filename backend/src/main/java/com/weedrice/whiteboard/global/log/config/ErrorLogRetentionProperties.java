package com.weedrice.whiteboard.global.log.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.error-log")
public class ErrorLogRetentionProperties {

    @Min(1)
    private int clientRetentionDays = 30;

    @Min(1)
    private int resolvedRetentionDays = 90;

    @Min(1)
    private int cleanupBatchSize = 500;
}
