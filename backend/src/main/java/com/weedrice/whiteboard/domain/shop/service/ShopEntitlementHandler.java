package com.weedrice.whiteboard.domain.shop.service;

import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import com.weedrice.whiteboard.domain.user.entity.User;

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

    default PurchasePreparation preparePurchase(User user, ShopItem item) {
        return preparePurchase(user.getUserId(), item);
    }

    default boolean supportsValidatedPurchasePreparation() {
        return false;
    }

    default PurchasePreparation prepareValidatedPurchase(Long userId, ShopItem item, Runnable afterConfigurationValidation) {
        validateConfiguration(item);
        afterConfigurationValidation.run();
        return preparePurchase(userId, item);
    }

    default PurchasePreparation prepareValidatedPurchase(User user, ShopItem item, Runnable afterConfigurationValidation) {
        validateConfiguration(item);
        afterConfigurationValidation.run();
        return preparePurchase(user, item);
    }

    void grant(PurchasePreparation preparation);
}
