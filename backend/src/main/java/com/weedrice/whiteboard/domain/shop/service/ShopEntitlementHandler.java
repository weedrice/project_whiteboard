package com.weedrice.whiteboard.domain.shop.service;

import com.weedrice.whiteboard.domain.shop.entity.ShopItem;

import java.util.Set;

public interface ShopEntitlementHandler {
    Set<String> getSupportedItemTypes();

    default boolean supports(String itemType) {
        return itemType != null && getSupportedItemTypes().contains(itemType);
    }

    void validateConfiguration(ShopItem item);

    void preflightPurchase(Long userId, ShopItem item);

    void grant(Long userId, ShopItem item);
}
