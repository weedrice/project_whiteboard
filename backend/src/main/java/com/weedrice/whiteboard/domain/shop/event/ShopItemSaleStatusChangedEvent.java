package com.weedrice.whiteboard.domain.shop.event;

public record ShopItemSaleStatusChangedEvent(
        Long itemId,
        String itemType,
        Long targetId,
        boolean saleEnabled) {
}
