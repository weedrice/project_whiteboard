package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.config.ShopStreamRedisProperties;
import com.weedrice.whiteboard.domain.notification.web.NotificationSseEmitterRegistry;
import com.weedrice.whiteboard.domain.shop.event.ShopItemSaleStatusChangedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Slf4j
public class RedisShopItemSaleStatusRelay implements ShopItemSaleStatusRelay, MessageListener {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationSseEmitterRegistry notificationSseEmitterRegistry;
    private final ShopStreamRedisProperties properties;
    private final Counter publishFailures;
    private final Counter receiveFailures;

    public RedisShopItemSaleStatusRelay(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            NotificationSseEmitterRegistry notificationSseEmitterRegistry,
            ShopStreamRedisProperties properties,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.notificationSseEmitterRegistry = notificationSseEmitterRegistry;
        this.properties = properties;
        this.publishFailures = meterRegistry.counter("noviis.shop.stream.relay.publish.failures");
        this.receiveFailures = meterRegistry.counter("noviis.shop.stream.relay.receive.failures");
    }

    @Override
    public void publish(ShopItemSaleStatusChangedEvent event) {
        if (event == null) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(RelayMessage.from(properties.getInstanceId(), event));
            redisTemplate.convertAndSend(properties.getChannel(), payload);
        } catch (RuntimeException exception) {
            publishFailures.increment();
            log.warn("Failed to relay shop sale status. itemId={}, exceptionType={}",
                    event.itemId(), exception.getClass().getSimpleName());
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            RelayMessage relayMessage = objectMapper.readValue(payload, RelayMessage.class);
            if (properties.getInstanceId().equals(relayMessage.sourceInstanceId())) {
                return;
            }
            ShopItemSaleStatusChangedEvent event = relayMessage.toEvent();
            validate(event);
            notificationSseEmitterRegistry.publishShopItemSaleStatusChanged(event);
        } catch (RuntimeException exception) {
            receiveFailures.increment();
            log.warn("Failed to consume relayed shop sale status. exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }

    private void validate(ShopItemSaleStatusChangedEvent event) {
        if (event.itemId() == null || event.itemType() == null || event.itemType().isBlank()) {
            throw new IllegalArgumentException("Invalid shop sale status relay message");
        }
    }

    record RelayMessage(
            String sourceInstanceId,
            Long itemId,
            String itemType,
            Long targetId,
            boolean saleEnabled) {

        static RelayMessage from(String sourceInstanceId, ShopItemSaleStatusChangedEvent event) {
            return new RelayMessage(
                    sourceInstanceId,
                    event.itemId(),
                    event.itemType(),
                    event.targetId(),
                    event.saleEnabled());
        }

        ShopItemSaleStatusChangedEvent toEvent() {
            return new ShopItemSaleStatusChangedEvent(itemId, itemType, targetId, saleEnabled);
        }
    }
}
