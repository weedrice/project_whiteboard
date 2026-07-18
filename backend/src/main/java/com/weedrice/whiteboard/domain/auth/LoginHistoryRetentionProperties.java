package com.weedrice.whiteboard.domain.auth;

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
@ConfigurationProperties(prefix = "app.login-history")
public class LoginHistoryRetentionProperties {

    @Positive
    private int retentionDays = 180;

    @Positive
    private int cleanupBatchSize = 500;

    @Positive
    private int cleanupMaxBatches = 20;
}
