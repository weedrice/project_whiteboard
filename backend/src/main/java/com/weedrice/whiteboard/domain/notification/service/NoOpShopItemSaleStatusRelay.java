package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.shop.event.ShopItemSaleStatusChangedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.shop-stream.redis",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true)
class NoOpShopItemSaleStatusRelay implements ShopItemSaleStatusRelay {

    @Override
    public void publish(ShopItemSaleStatusChangedEvent event) {
        // Local SSE delivery remains active when the shared relay is disabled.
    }
}
