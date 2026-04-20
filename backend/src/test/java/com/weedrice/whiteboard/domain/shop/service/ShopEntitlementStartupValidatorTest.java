package com.weedrice.whiteboard.domain.shop.service;

import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import com.weedrice.whiteboard.domain.shop.repository.ShopItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShopEntitlementStartupValidator test")
class ShopEntitlementStartupValidatorTest {

    @Mock
    private ShopItemRepository shopItemRepository;
    @Mock
    private ShopEntitlementCapabilityRegistry shopEntitlementCapabilityRegistry;

    @InjectMocks
    private ShopEntitlementStartupValidator validator;

    @Test
    @DisplayName("Fails startup when an active item has no handler")
    void validateActiveItems_missingHandler() {
        ShopItem item = shopItem(1L, "DECORATION", null);
        when(shopItemRepository.findByIsActive(true)).thenReturn(List.of(item));
        when(shopEntitlementCapabilityRegistry.supports(item)).thenReturn(false);

        assertThatThrownBy(() -> validator.validateActiveItems())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing-handler");
    }

    @Test
    @DisplayName("Fails startup when an active item has an invalid target")
    void validateActiveItems_invalidTarget() {
        ShopItem item = shopItem(1L, "EMOTICON", null);
        when(shopItemRepository.findByIsActive(true)).thenReturn(List.of(item));
        when(shopEntitlementCapabilityRegistry.supports(item)).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("missing-targetId"))
                .when(shopEntitlementCapabilityRegistry)
                .validateConfiguration(item);

        assertThatThrownBy(() -> validator.validateActiveItems())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing-targetId");
    }

    @Test
    @DisplayName("Passes when every active item is valid")
    void validateActiveItems_success() {
        ShopItem item = shopItem(1L, "EMOTICON", 10L);
        when(shopItemRepository.findByIsActive(true)).thenReturn(List.of(item));
        when(shopEntitlementCapabilityRegistry.supports(item)).thenReturn(true);

        validator.validateActiveItems();

        verify(shopEntitlementCapabilityRegistry).validateConfiguration(item);
    }

    private ShopItem shopItem(Long itemId, String itemType, Long targetId) {
        ShopItem item = ShopItem.builder()
                .itemName("Shop item")
                .price(100)
                .itemType(itemType)
                .targetId(targetId)
                .build();
        ReflectionTestUtils.setField(item, "itemId", itemId);
        ReflectionTestUtils.setField(item, "isActive", true);
        return item;
    }
}
