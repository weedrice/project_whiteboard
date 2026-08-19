package com.weedrice.whiteboard.domain.notification.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.shop-stream.redis")
public class ShopStreamRedisProperties {

    private boolean enabled;

    @NotBlank
    private String channel = "noviis:shop:sale-status";

    @NotBlank
    private String instanceId = UUID.randomUUID().toString();
}
