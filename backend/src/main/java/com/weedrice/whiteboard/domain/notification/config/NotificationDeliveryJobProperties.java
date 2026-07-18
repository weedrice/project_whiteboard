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
@ConfigurationProperties(prefix = "notification.delivery-job")
public class NotificationDeliveryJobProperties {

    @Positive
    private int processingBatchSize = 100;

    @Positive
    private int cleanupBatchSize = 500;

    @Positive
    private int cleanupMaxBatches = 10;

    @Positive
    private int completedRetentionDays = 7;

    @Positive
    private int failedRetentionDays = 30;
}
