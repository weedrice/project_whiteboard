package com.weedrice.whiteboard.domain.notification.config;

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
@ConfigurationProperties(prefix = "web-push.delivery-job")
public class PushDeliveryJobProperties {

    @Positive
    private int processingBatchSize = 20;

    @Positive
    private int cleanupBatchSize = 500;

    @Positive
    private int cleanupMaxBatches = 10;

    @Positive
    private int terminalRetentionDays = 7;

    @Positive
    private int failedRetentionDays = 30;
}
