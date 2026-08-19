package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.config.ShopStreamRedisProperties;
import com.weedrice.whiteboard.domain.notification.web.NotificationSseEmitterRegistry;
import com.weedrice.whiteboard.domain.shop.event.ShopItemSaleStatusChangedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisShopItemSaleStatusRelayTest {

    private static final String CHANNEL = "test:shop:sale-status";

    private StringRedisTemplate redisTemplate;
    private NotificationSseEmitterRegistry notificationSseEmitterRegistry;
    private SimpleMeterRegistry meterRegistry;
    private RedisShopItemSaleStatusRelay relay;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        notificationSseEmitterRegistry = mock(NotificationSseEmitterRegistry.class);
        meterRegistry = new SimpleMeterRegistry();
        ShopStreamRedisProperties properties = new ShopStreamRedisProperties();
        properties.setChannel(CHANNEL);
        properties.setInstanceId("instance-a");
        relay = new RedisShopItemSaleStatusRelay(
                redisTemplate,
                JsonMapper.builder().build(),
                notificationSseEmitterRegistry,
                properties,
                meterRegistry);
    }

    @Test
    void publishesEventWithSourceInstance() {
        relay.publish(event(false));

        verify(redisTemplate).convertAndSend(
                CHANNEL,
                "{\"sourceInstanceId\":\"instance-a\",\"itemId\":3,\"itemType\":\"EMOTICON\","
                        + "\"targetId\":9,\"saleEnabled\":false}");
    }

    @Test
    void deliversRemoteInstanceEventToLocalClients() {
        ShopItemSaleStatusChangedEvent expected = event(true);

        relay.onMessage(message("""
                {"sourceInstanceId":"instance-b","itemId":3,"itemType":"EMOTICON",\
                "targetId":9,"saleEnabled":true}
                """), null);

        verify(notificationSseEmitterRegistry).publishShopItemSaleStatusChanged(expected);
    }

    @Test
    void ignoresEventEchoFromSameInstance() {
        relay.onMessage(message("""
                {"sourceInstanceId":"instance-a","itemId":3,"itemType":"EMOTICON",\
                "targetId":9,"saleEnabled":false}
                """), null);

        verify(notificationSseEmitterRegistry, never()).publishShopItemSaleStatusChanged(any());
    }

    @Test
    void isolatesRedisPublishFailure() {
        when(redisTemplate.convertAndSend(any(), any())).thenThrow(new IllegalStateException("redis unavailable"));

        relay.publish(event(false));

        assertThat(meterRegistry.counter("noviis.shop.stream.relay.publish.failures").count()).isEqualTo(1);
    }

    @Test
    void rejectsMalformedRemoteMessage() {
        relay.onMessage(message("{\"sourceInstanceId\":\"instance-b\",\"itemId\":null}"), null);

        verify(notificationSseEmitterRegistry, never()).publishShopItemSaleStatusChanged(any());
        assertThat(meterRegistry.counter("noviis.shop.stream.relay.receive.failures").count()).isEqualTo(1);
    }

    private Message message(String payload) {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(payload.getBytes(StandardCharsets.UTF_8));
        return message;
    }

    private ShopItemSaleStatusChangedEvent event(boolean saleEnabled) {
        return new ShopItemSaleStatusChangedEvent(3L, "EMOTICON", 9L, saleEnabled);
    }
}
