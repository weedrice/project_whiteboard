package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.web.NotificationSseEmitterRegistry;
import com.weedrice.whiteboard.domain.shop.event.ShopItemSaleStatusChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void broadcastsAfterCommitOnDedicatedStreamExecutor() throws NoSuchMethodException {
        Method handle = ShopItemSaleStatusChangedEventListener.class
                .getMethod("handle", ShopItemSaleStatusChangedEvent.class);

        assertThat(handle.getAnnotation(Async.class).value())
                .isEqualTo("streamTaskExecutor");
        assertThat(handle.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
