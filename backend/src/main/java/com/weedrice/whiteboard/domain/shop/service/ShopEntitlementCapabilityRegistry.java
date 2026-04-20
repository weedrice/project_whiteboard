package com.weedrice.whiteboard.domain.shop.service;

import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
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
        return findHandler(itemType).isPresent();
    }

    public void validateConfiguration(ShopItem item) {
        findRequiredHandler(item.getItemType()).validateConfiguration(item);
    }

    public void grant(Long userId, ShopItem item) {
        findRequiredHandler(item.getItemType()).grant(userId, item);
    }

    public Set<String> getSupportedItemTypes() {
        return handlers.stream()
                .flatMap(handler -> handler.getSupportedItemTypes().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    private Optional<ShopEntitlementHandler> findHandler(String itemType) {
        return handlers.stream()
                .filter(handler -> handler.supports(itemType))
                .findFirst();
    }

    private ShopEntitlementHandler findRequiredHandler(String itemType) {
        return findHandler(itemType)
                .orElseThrow(() -> new IllegalStateException("No shop entitlement handler registered for itemType=" + itemType));
    }
}
