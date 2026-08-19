package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.web.NotificationSseEmitterRegistry;
import com.weedrice.whiteboard.domain.shop.event.ShopItemSaleStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ShopItemSaleStatusChangedEventListener {

    private final NotificationSseEmitterRegistry notificationSseEmitterRegistry;

    @Async("streamTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ShopItemSaleStatusChangedEvent event) {
        notificationSseEmitterRegistry.publishShopItemSaleStatusChanged(event);
    }
}
