package com.weedrice.whiteboard.domain.notification.config;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "notification.stream")
public class NotificationStreamProperties {

    @Positive
    private long timeoutMillis = 1_800_000L;

    @Positive
    private int maxConnectionsPerUser = 5;
}
