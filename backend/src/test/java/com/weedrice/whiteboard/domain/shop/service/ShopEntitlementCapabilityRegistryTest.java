package com.weedrice.whiteboard.domain.shop.service;

import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ShopEntitlementCapabilityRegistry 테스트")
class ShopEntitlementCapabilityRegistryTest {

    @Test
    @DisplayName("handler가 없으면 fail-closed로 동작한다")
    void getSupportedItemTypes_withoutHandlers_returnsEmpty() {
        ShopEntitlementCapabilityRegistry registry = new ShopEntitlementCapabilityRegistry(List.of());

        assertThat(registry.getSupportedItemTypes()).isEmpty();
        assertThat(registry.supports("EMOTICON")).isFalse();
    }

    @Test
    @DisplayName("등록된 handler 타입만 지원한다")
    void getSupportedItemTypes_withHandlers_returnsUnion() {
        ShopEntitlementCapabilityRegistry registry = new ShopEntitlementCapabilityRegistry(List.of(
                () -> Set.of("EMOTICON"),
                () -> Set.of("DECORATION")));

        ShopItem supportedItem = ShopItem.builder()
                .itemName("지원 상품")
                .price(100)
                .itemType("EMOTICON")
                .build();
        ShopItem unsupportedItem = ShopItem.builder()
                .itemName("미지원 상품")
                .price(100)
                .itemType("BADGE")
                .build();

        assertThat(registry.getSupportedItemTypes()).containsExactlyInAnyOrder("EMOTICON", "DECORATION");
        assertThat(registry.supports("DECORATION")).isTrue();
        assertThat(registry.supports(supportedItem)).isTrue();
        assertThat(registry.supports(unsupportedItem)).isFalse();
    }
}
