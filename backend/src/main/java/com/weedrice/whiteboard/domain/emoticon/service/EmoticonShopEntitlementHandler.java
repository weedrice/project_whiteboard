package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import com.weedrice.whiteboard.domain.shop.service.ShopEntitlementHandler;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
class EmoticonShopEntitlementHandler implements ShopEntitlementHandler {

    private final EmoticonEntitlementGrantService emoticonEntitlementGrantService;

    EmoticonShopEntitlementHandler(EmoticonEntitlementGrantService emoticonEntitlementGrantService) {
        this.emoticonEntitlementGrantService = emoticonEntitlementGrantService;
    }

    @Override
    public Set<String> getSupportedItemTypes() {
        return Set.of(EmoticonShopItemTypes.EMOTICON);
    }

    @Override
    public void validateConfiguration(ShopItem item) {
        emoticonEntitlementGrantService.validateTargetConfiguration(item.getTargetId());
    }

    @Override
    public PurchasePreparation preparePurchase(Long userId, ShopItem item) {
        return new EmoticonPurchasePreparation(
                emoticonEntitlementGrantService.prepareGrant(userId, item.getTargetId()),
                item.getPrice());
    }

    @Override
    public boolean supportsValidatedPurchasePreparation() {
        return true;
    }

    @Override
    public PurchasePreparation prepareValidatedPurchase(Long userId, ShopItem item, Runnable afterConfigurationValidation) {
        return new EmoticonPurchasePreparation(
                emoticonEntitlementGrantService.prepareConfiguredGrant(
                        userId,
                        item.getTargetId(),
                        afterConfigurationValidation),
                item.getPrice());
    }

    @Override
    public PurchasePreparation prepareValidatedPurchase(User user, ShopItem item, Runnable afterConfigurationValidation) {
        return new EmoticonPurchasePreparation(
                emoticonEntitlementGrantService.prepareConfiguredGrant(
                        user,
                        item.getTargetId(),
                        afterConfigurationValidation),
                item.getPrice());
    }

    @Override
    public void grant(PurchasePreparation preparation) {
        EmoticonPurchasePreparation emoticonPreparation = (EmoticonPurchasePreparation) preparation;
        emoticonEntitlementGrantService.grant(
                emoticonPreparation.grantContext(),
                emoticonPreparation.purchasedPrice());
    }

    private record EmoticonPurchasePreparation(
            EmoticonEntitlementGrantService.EmoticonGrantContext grantContext,
            int purchasedPrice) implements PurchasePreparation {
    }
}
