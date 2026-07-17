package com.weedrice.whiteboard.domain.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NotificationStreamProperties.class)
public class NotificationStreamConfig {
}
