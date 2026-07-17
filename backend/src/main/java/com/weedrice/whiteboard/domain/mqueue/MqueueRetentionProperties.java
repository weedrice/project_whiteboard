package com.weedrice.whiteboard.domain.mqueue;

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
@ConfigurationProperties(prefix = "app.message-queue")
public class MqueueRetentionProperties {

    @Positive
    private int terminalRetentionDays = 30;

    @Positive
    private int cleanupBatchSize = 500;
}
