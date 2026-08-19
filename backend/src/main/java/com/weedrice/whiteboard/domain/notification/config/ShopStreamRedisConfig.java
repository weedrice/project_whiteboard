package com.weedrice.whiteboard.domain.notification.config;

import com.weedrice.whiteboard.domain.notification.service.RedisShopItemSaleStatusRelay;
import com.weedrice.whiteboard.domain.notification.web.NotificationSseEmitterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.shop-stream.redis", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ShopStreamRedisProperties.class)
public class ShopStreamRedisConfig {

    @Bean
    public RedisShopItemSaleStatusRelay redisShopItemSaleStatusRelay(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            NotificationSseEmitterRegistry notificationSseEmitterRegistry,
            ShopStreamRedisProperties properties,
            MeterRegistry meterRegistry) {
        return new RedisShopItemSaleStatusRelay(
                redisTemplate,
                objectMapper,
                notificationSseEmitterRegistry,
                properties,
                meterRegistry);
    }

    @Bean
    public RedisMessageListenerContainer shopStreamRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisShopItemSaleStatusRelay relay,
            ShopStreamRedisProperties properties) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(relay, new ChannelTopic(properties.getChannel()));
        return container;
    }
}
