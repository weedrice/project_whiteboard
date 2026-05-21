package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonMaster;
import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import com.weedrice.whiteboard.domain.shop.service.ShopEntitlementHandler;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmoticonShopEntitlementHandler test")
class EmoticonShopEntitlementHandlerTest {

    @Mock
    private EmoticonEntitlementGrantService emoticonEntitlementGrantService;

    private EmoticonShopEntitlementHandler handler;
    private ShopItem shopItem;
    private EmoticonEntitlementGrantService.EmoticonGrantContext grantContext;
    private User buyer;

    @BeforeEach
    void setUp() {
        handler = new EmoticonShopEntitlementHandler(emoticonEntitlementGrantService);

        shopItem = ShopItem.builder()
                .itemName("Premium emoticon")
                .price(100)
                .itemType("EMOTICON")
                .targetId(10L)
                .build();

        buyer = User.builder()
                .loginId("buyer")
                .displayName("buyer")
                .email("buyer@example.com")
                .password("encoded")
                .build();
        ReflectionTestUtils.setField(buyer, "userId", 1L);

        User creator = User.builder()
                .loginId("creator")
                .displayName("creator")
                .email("creator@example.com")
                .password("encoded")
                .build();
        ReflectionTestUtils.setField(creator, "userId", 2L);

        EmoticonMaster emoticon = EmoticonMaster.builder()
                .name("hello")
                .thumbnailUrl("thumb")
                .tags(List.of("tag"))
                .creator(creator)
                .build();
        ReflectionTestUtils.setField(emoticon, "emoticonId", 10L);

        grantContext = new EmoticonEntitlementGrantService.EmoticonGrantContext(buyer, emoticon);
    }

    @Test
    @DisplayName("Delegates target validation using targetId")
    void validateConfiguration_delegatesWithTargetId() {
        handler.validateConfiguration(shopItem);

        verify(emoticonEntitlementGrantService).validateTargetConfiguration(10L);
    }

    @Test
    @DisplayName("Prepares purchase using targetId and shop price")
    void preparePurchase_delegatesWithTargetId() {
        when(emoticonEntitlementGrantService.prepareGrant(1L, 10L)).thenReturn(grantContext);

        ShopEntitlementHandler.PurchasePreparation preparation = handler.preparePurchase(1L, shopItem);

        verify(emoticonEntitlementGrantService).prepareGrant(1L, 10L);
        handler.grant(preparation);
        verify(emoticonEntitlementGrantService).grant(grantContext, 100);
    }

    @Test
    @DisplayName("Prepares validated purchase using one configured grant lookup")
    void prepareValidatedPurchase_delegatesWithTargetId() {
        when(emoticonEntitlementGrantService.prepareConfiguredGrant(eq(buyer), eq(10L), any(Runnable.class)))
                .thenReturn(grantContext);

        ShopEntitlementHandler.PurchasePreparation preparation = handler.prepareValidatedPurchase(buyer, shopItem, () -> {
        });

        verify(emoticonEntitlementGrantService).prepareConfiguredGrant(eq(buyer), eq(10L), any(Runnable.class));
        handler.grant(preparation);
        verify(emoticonEntitlementGrantService).grant(grantContext, 100);
    }

    @Test
    @DisplayName("Delegates grant using prepared context and shop price")
    void grant_delegatesContextAndPrice() {
        when(emoticonEntitlementGrantService.prepareGrant(1L, 10L)).thenReturn(grantContext);

        ShopEntitlementHandler.PurchasePreparation preparation = handler.preparePurchase(1L, shopItem);
        handler.grant(preparation);

        verify(emoticonEntitlementGrantService).prepareGrant(1L, 10L);
        verify(emoticonEntitlementGrantService).grant(grantContext, 100);
    }
}
