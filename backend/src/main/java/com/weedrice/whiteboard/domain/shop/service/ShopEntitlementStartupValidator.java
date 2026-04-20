package com.weedrice.whiteboard.domain.shop.service;

import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import com.weedrice.whiteboard.domain.shop.repository.ShopItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ShopEntitlementStartupValidator implements SmartInitializingSingleton {

    private final ShopItemRepository shopItemRepository;
    private final ShopEntitlementCapabilityRegistry shopEntitlementCapabilityRegistry;

    @Override
    public void afterSingletonsInstantiated() {
        validateActiveItems();
    }

    void validateActiveItems() {
        List<String> invalidItems = shopItemRepository.findByIsActive(true).stream()
                .map(this::validateItem)
                .filter(message -> !message.isBlank())
                .toList();

        if (!invalidItems.isEmpty()) {
            throw new IllegalStateException("Invalid active shop items: " + String.join("; ", invalidItems));
        }
    }

    private String validateItem(ShopItem item) {
        if (!shopEntitlementCapabilityRegistry.supports(item)) {
            return "itemId=" + item.getItemId() + ", itemType=" + item.getItemType() + ", reason=missing-handler";
        }

        try {
            shopEntitlementCapabilityRegistry.validateConfiguration(item);
            return "";
        } catch (IllegalStateException ex) {
            return "itemId=" + item.getItemId() + ", itemType=" + item.getItemType() + ", reason=" + ex.getMessage();
        }
    }
}
