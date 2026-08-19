package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.web.NotificationSseEmitterRegistry;
import com.weedrice.whiteboard.domain.shop.event.ShopItemSaleStatusChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShopItemSaleStatusChangedEventListenerTest {

    @Mock
    private NotificationSseEmitterRegistry notificationSseEmitterRegistry;

    @Test
    void broadcastsCommittedSaleStatusChange() {
        ShopItemSaleStatusChangedEvent event =
                new ShopItemSaleStatusChangedEvent(3L, "EMOTICON", 9L, false);
        ShopItemSaleStatusChangedEventListener listener =
                new ShopItemSaleStatusChangedEventListener(notificationSseEmitterRegistry);

        listener.handle(event);

        verify(notificationSseEmitterRegistry).publishShopItemSaleStatusChanged(event);
    }
}
