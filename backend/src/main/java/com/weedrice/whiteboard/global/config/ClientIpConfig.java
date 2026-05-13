package com.weedrice.whiteboard.global.config;

import com.weedrice.whiteboard.global.common.util.ClientIpProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ClientIpProperties.class)
public class ClientIpConfig {
}
