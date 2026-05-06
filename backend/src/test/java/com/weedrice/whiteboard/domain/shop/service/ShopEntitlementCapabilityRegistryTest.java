package com.weedrice.whiteboard.domain.shop.service;

import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ShopEntitlementCapabilityRegistry test")
class ShopEntitlementCapabilityRegistryTest {

    @Test
    @DisplayName("Returns empty when there is no handler")
    void getSupportedItemTypes_withoutHandlers_returnsEmpty() {
        ShopEntitlementCapabilityRegistry registry = new ShopEntitlementCapabilityRegistry(List.of());

        assertThat(registry.getSupportedItemTypes()).isEmpty();
        assertThat(registry.supports("EMOTICON")).isFalse();
    }

    @Test
    @DisplayName("Collects supported item types from handlers")
    void getSupportedItemTypes_withHandlers_returnsUnion() {
        ShopEntitlementCapabilityRegistry registry = new ShopEntitlementCapabilityRegistry(List.of(
                new TestHandler(Set.of("EMOTICON")),
                new TestHandler(Set.of("DECORATION"))));

        ShopItem supportedItem = ShopItem.builder()
                .itemName("Supported item")
                .price(100)
                .itemType("EMOTICON")
                .targetId(1L)
                .build();
        ShopItem unsupportedItem = ShopItem.builder()
                .itemName("Unsupported item")
                .price(100)
                .itemType("BADGE")
                .build();

        assertThat(registry.getSupportedItemTypes()).containsExactlyInAnyOrder("EMOTICON", "DECORATION");
        assertThat(registry.supports("DECORATION")).isTrue();
        assertThat(registry.supports(supportedItem)).isTrue();
        assertThat(registry.supports(unsupportedItem)).isFalse();
    }

    @Test
    @DisplayName("Delegates validate, prepare, and grant to the matching handler")
    void validatePrepareAndGrant_delegateToMatchingHandler() {
        TrackingHandler handler = new TrackingHandler(Set.of("EMOTICON"));
        ShopEntitlementCapabilityRegistry registry = new ShopEntitlementCapabilityRegistry(List.of(handler));
        ShopItem item = ShopItem.builder()
                .itemName("Supported item")
                .price(100)
                .itemType("EMOTICON")
                .targetId(1L)
                .build();

        registry.validateConfiguration(item);
        ShopEntitlementCapabilityRegistry.PreparedPurchase preparedPurchase =
                registry.preparePurchase(1L, item);
        registry.grant(preparedPurchase);

        assertThat(handler.validatedItem).isSameAs(item);
        assertThat(handler.prepareUserId).isEqualTo(1L);
        assertThat(handler.prepareItem).isSameAs(item);
        assertThat(handler.grantedPreparation).isSameAs(handler.preparation);
    }

    private enum TestPreparation implements ShopEntitlementHandler.PurchasePreparation {
        INSTANCE
    }

    private record TestHandler(Set<String> supportedItemTypes) implements ShopEntitlementHandler {
        @Override
        public Set<String> getSupportedItemTypes() {
            return supportedItemTypes;
        }

        @Override
        public void validateConfiguration(ShopItem item) {
        }

        @Override
        public PurchasePreparation preparePurchase(Long userId, ShopItem item) {
            return TestPreparation.INSTANCE;
        }

        @Override
        public void grant(PurchasePreparation preparation) {
        }
    }

    private static final class TrackingHandler implements ShopEntitlementHandler {
        private final Set<String> supportedItemTypes;
        private final PurchasePreparation preparation = TestPreparation.INSTANCE;
        private ShopItem validatedItem;
        private Long prepareUserId;
        private ShopItem prepareItem;
        private PurchasePreparation grantedPreparation;

        private TrackingHandler(Set<String> supportedItemTypes) {
            this.supportedItemTypes = supportedItemTypes;
        }

        @Override
        public Set<String> getSupportedItemTypes() {
            return supportedItemTypes;
        }

        @Override
        public void validateConfiguration(ShopItem item) {
            validatedItem = item;
        }

        @Override
        public PurchasePreparation preparePurchase(Long userId, ShopItem item) {
            prepareUserId = userId;
            prepareItem = item;
            return preparation;
        }

        @Override
        public void grant(PurchasePreparation preparation) {
            grantedPreparation = preparation;
        }
    }
}
