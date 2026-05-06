package com.weedrice.whiteboard.domain.shop.service;

import com.weedrice.whiteboard.domain.shop.entity.ShopItem;

import java.util.Set;

public interface ShopEntitlementHandler {
    interface PurchasePreparation {
    }

    Set<String> getSupportedItemTypes();

    default boolean supports(String itemType) {
        return itemType != null && getSupportedItemTypes().contains(itemType);
    }

    void validateConfiguration(ShopItem item);

    PurchasePreparation preparePurchase(Long userId, ShopItem item);

    void grant(PurchasePreparation preparation);
}
