package com.weedrice.whiteboard.domain.shop.service;

import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ShopEntitlementCapabilityRegistry {

    private final List<ShopEntitlementHandler> handlers;

    public ShopEntitlementCapabilityRegistry(List<ShopEntitlementHandler> handlers) {
        this.handlers = handlers;
    }

    public boolean supports(ShopItem item) {
        return item != null && supports(item.getItemType());
    }

    public boolean supports(String itemType) {
        return itemType != null && getSupportedItemTypes().contains(itemType);
    }

    public Set<String> getSupportedItemTypes() {
        // Fail closed until a concrete entitlement handler is introduced for a shop item type.
        return handlers.stream()
                .flatMap(handler -> handler.getSupportedItemTypes().stream())
                .collect(Collectors.toUnmodifiableSet());
    }
}
