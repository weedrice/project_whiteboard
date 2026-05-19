package com.weedrice.whiteboard.domain.shop.service;

import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import com.weedrice.whiteboard.domain.shop.repository.ShopItemRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        List<ShopItem> activeItems = shopItemRepository.findByIsActive(true);
        List<String> invalidItems = activeItems.stream()
                .map(this::validateItem)
                .filter(message -> !message.isBlank())
                .toList();
        List<String> duplicateItems = findDuplicateSupportedItems(activeItems);

        if (!invalidItems.isEmpty() || !duplicateItems.isEmpty()) {
            throw new IllegalStateException("Invalid active shop items: "
                    + String.join("; ", concat(invalidItems, duplicateItems)));
        }
    }

    private String validateItem(ShopItem item) {
        if (!shopEntitlementCapabilityRegistry.supports(item)) {
            return "itemId=" + item.getItemId() + ", itemType=" + item.getItemType() + ", reason=missing-handler";
        }

        try {
            shopEntitlementCapabilityRegistry.validateConfiguration(item);
            return "";
        } catch (BusinessException ex) {
            return "itemId=" + item.getItemId() + ", itemType=" + item.getItemType() + ", reason="
                    + ex.getErrorCode().name();
        } catch (IllegalStateException ex) {
            return "itemId=" + item.getItemId() + ", itemType=" + item.getItemType() + ", reason=" + ex.getMessage();
        }
    }

    private List<String> findDuplicateSupportedItems(List<ShopItem> activeItems) {
        Map<ItemEntitlementKey, List<ShopItem>> itemsByEntitlement = new LinkedHashMap<>();
        activeItems.stream()
                .filter(shopEntitlementCapabilityRegistry::supports)
                .forEach(item -> itemsByEntitlement
                        .computeIfAbsent(ItemEntitlementKey.from(item), key -> new java.util.ArrayList<>())
                        .add(item));

        return itemsByEntitlement.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> "itemType=" + entry.getKey().itemType()
                        + ", targetId=" + entry.getKey().targetId()
                        + ", reason=duplicate-active-entitlement"
                        + ", itemIds=" + entry.getValue().stream()
                        .map(item -> String.valueOf(item.getItemId()))
                        .toList())
                .toList();
    }

    private List<String> concat(List<String> first, List<String> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }

    private record ItemEntitlementKey(String itemType, Long targetId) {
        private static ItemEntitlementKey from(ShopItem item) {
            return new ItemEntitlementKey(item.getItemType(), item.getTargetId());
        }
    }
}
