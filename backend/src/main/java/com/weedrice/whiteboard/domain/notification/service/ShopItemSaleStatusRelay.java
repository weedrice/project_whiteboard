package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.shop.event.ShopItemSaleStatusChangedEvent;

public interface ShopItemSaleStatusRelay {

    void publish(ShopItemSaleStatusChangedEvent event);
}
