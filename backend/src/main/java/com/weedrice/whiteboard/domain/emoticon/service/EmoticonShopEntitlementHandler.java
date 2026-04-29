package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import com.weedrice.whiteboard.domain.shop.service.ShopEntitlementHandler;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
class EmoticonShopEntitlementHandler implements ShopEntitlementHandler {

    private static final String ITEM_TYPE = "EMOTICON";

    private final EmoticonEntitlementGrantService emoticonEntitlementGrantService;

    EmoticonShopEntitlementHandler(EmoticonEntitlementGrantService emoticonEntitlementGrantService) {
        this.emoticonEntitlementGrantService = emoticonEntitlementGrantService;
    }

    @Override
    public Set<String> getSupportedItemTypes() {
        return Set.of(ITEM_TYPE);
    }

    @Override
    public void validateConfiguration(ShopItem item) {
        emoticonEntitlementGrantService.validateTargetConfiguration(item.getTargetId());
    }

    @Override
    public void preflightPurchase(Long userId, ShopItem item) {
        emoticonEntitlementGrantService.preflightGrant(userId, item.getTargetId());
    }

    @Override
    public void grant(Long userId, ShopItem item) {
        EmoticonEntitlementGrantService.EmoticonGrantContext grantContext =
                emoticonEntitlementGrantService.prepareGrant(userId, item.getTargetId());
        emoticonEntitlementGrantService.grant(grantContext, item.getPrice());
    }
}
