package com.weedrice.whiteboard.domain.shop.service;

import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.shop.dto.ShopItemResponse;
import com.weedrice.whiteboard.domain.shop.entity.PurchaseHistory;
import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import com.weedrice.whiteboard.domain.shop.repository.PurchaseHistoryRepository;
import com.weedrice.whiteboard.domain.shop.repository.ShopItemRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShopService test")
class ShopServiceTest {

    @Mock
    private ShopItemRepository shopItemRepository;
    @Mock
    private PurchaseHistoryRepository purchaseHistoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PointService pointService;
    @Mock
    private ShopEntitlementCapabilityRegistry shopEntitlementCapabilityRegistry;
    @Mock
    private SanctionService sanctionService;

    @InjectMocks
    private ShopService shopService;

    private User user;
    private ShopItem emoticonItem;
    private ShopItem decorationItem;

    @BeforeEach
    void setUp() {
        user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        emoticonItem = ShopItem.builder()
                .itemName("Premium emoticon")
                .description("Shop item")
                .price(100)
                .itemType("EMOTICON")
                .targetId(10L)
                .imageUrl("https://example.com/emoticon.png")
                .build();
        ReflectionTestUtils.setField(emoticonItem, "itemId", 2L);
        ReflectionTestUtils.setField(emoticonItem, "isActive", true);

        decorationItem = ShopItem.builder()
                .itemName("Decoration")
                .description("Decoration item")
                .price(500)
                .itemType("DECORATION")
                .build();
        ReflectionTestUtils.setField(decorationItem, "itemId", 3L);
        ReflectionTestUtils.setField(decorationItem, "isActive", true);
    }

    @Nested
    @DisplayName("Get shop items")
    class GetShopItems {

        @Test
        @DisplayName("Returns a supported item type")
        void getShopItems_supportedType() {
            Pageable pageable = PageRequest.of(0, 20);
            Pageable expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Order.asc("itemId")));
            when(shopEntitlementCapabilityRegistry.supports("EMOTICON")).thenReturn(true);
            when(shopEntitlementCapabilityRegistry.getSupportedItemTypes()).thenReturn(Set.of("EMOTICON"));
            when(shopItemRepository.findByIsActiveAndItemType(true, "EMOTICON", expectedPageable))
                    .thenReturn(new PageImpl<>(List.of(emoticonItem), expectedPageable, 1));

            ShopItemResponse response = shopService.getShopItems("EMOTICON", pageable);

            assertThat(response.getContent()).hasSize(1);
            verify(shopItemRepository).findByIsActiveAndItemType(true, "EMOTICON", expectedPageable);
        }

        @Test
        @DisplayName("Returns empty when there is no supported handler")
        void getShopItems_withoutSupportedHandlers_returnsEmpty() {
            Pageable pageable = PageRequest.of(0, 20);
            when(shopEntitlementCapabilityRegistry.getSupportedItemTypes()).thenReturn(Set.of());

            ShopItemResponse response = shopService.getShopItems("", pageable);

            assertThat(response.getContent()).isEmpty();
            verify(shopItemRepository, never()).findByIsActive(any(), any());
            verify(shopItemRepository, never()).findByIsActiveAndItemTypeIn(any(), any(), any());
        }

        @Test
        @DisplayName("Returns all supported item types")
        void getShopItems_allTypes_usesSupportedTypes() {
            Pageable pageable = PageRequest.of(0, 20);
            Pageable expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Order.asc("itemId")));
            Set<String> supportedTypes = Set.of("EMOTICON", "DECORATION");
            when(shopEntitlementCapabilityRegistry.getSupportedItemTypes()).thenReturn(supportedTypes);
            when(shopItemRepository.findByIsActiveAndItemTypeIn(true, supportedTypes, expectedPageable))
                    .thenReturn(new PageImpl<>(List.of(emoticonItem, decorationItem), expectedPageable, 2));

            ShopItemResponse response = shopService.getShopItems(null, pageable);

            assertThat(response.getContent()).hasSize(2);
            verify(shopItemRepository).findByIsActiveAndItemTypeIn(true, supportedTypes, expectedPageable);
        }

        @Test
        @DisplayName("Returns empty for an unsupported type filter")
        void getShopItems_unsupportedType_returnsEmpty() {
            Pageable pageable = PageRequest.of(0, 20);
            when(shopEntitlementCapabilityRegistry.supports("EMOTICON")).thenReturn(false);

            ShopItemResponse response = shopService.getShopItems("EMOTICON", pageable);

            assertThat(response.getContent()).isEmpty();
            verify(shopItemRepository, never()).findByIsActiveAndItemType(any(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("Purchase item")
    class PurchaseItem {

        @Test
        @DisplayName("Purchases a supported item and grants the entitlement")
        void purchaseItem_success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(shopItemRepository.findById(2L)).thenReturn(Optional.of(emoticonItem));
            when(shopEntitlementCapabilityRegistry.supports(emoticonItem)).thenReturn(true);

            PurchaseHistory savedPurchaseHistory = PurchaseHistory.builder()
                    .user(user)
                    .item(emoticonItem)
                    .purchasedPrice(emoticonItem.getPrice())
                    .build();
            ReflectionTestUtils.setField(savedPurchaseHistory, "purchaseId", 1L);
            when(purchaseHistoryRepository.save(any(PurchaseHistory.class))).thenReturn(savedPurchaseHistory);

            Long purchaseId = shopService.purchaseItem(1L, 2L);

            assertThat(purchaseId).isEqualTo(1L);
            InOrder inOrder = inOrder(
                    sanctionService,
                    shopEntitlementCapabilityRegistry,
                    pointService,
                    purchaseHistoryRepository);
            inOrder.verify(sanctionService).validateNotBanned(user);
            inOrder.verify(shopEntitlementCapabilityRegistry).validateConfiguration(emoticonItem);
            inOrder.verify(shopEntitlementCapabilityRegistry).preflightPurchase(1L, emoticonItem);
            inOrder.verify(pointService).spendPoint(
                    eq(1L),
                    eq(100),
                    eq("Shop item purchase: Premium emoticon"),
                    eq(2L),
                    eq("SHOP_ITEM"));
            inOrder.verify(shopEntitlementCapabilityRegistry).grant(1L, emoticonItem);
            inOrder.verify(purchaseHistoryRepository).save(any(PurchaseHistory.class));
        }

        @Test
        @DisplayName("Blocks inactive items")
        void purchaseItem_inactiveItem() {
            ReflectionTestUtils.setField(emoticonItem, "isActive", false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(shopItemRepository.findById(2L)).thenReturn(Optional.of(emoticonItem));

            assertThatThrownBy(() -> shopService.purchaseItem(1L, 2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITEM_NOT_AVAILABLE);

            verify(pointService, never()).spendPoint(anyLong(), anyInt(), anyString(), anyLong(), anyString());
            verify(purchaseHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Blocks unsupported items")
        void purchaseItem_unsupportedItem() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(shopItemRepository.findById(2L)).thenReturn(Optional.of(emoticonItem));
            when(shopEntitlementCapabilityRegistry.supports(emoticonItem)).thenReturn(false);

            assertThatThrownBy(() -> shopService.purchaseItem(1L, 2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITEM_NOT_AVAILABLE);

            verify(pointService, never()).spendPoint(anyLong(), anyInt(), anyString(), anyLong(), anyString());
            verify(purchaseHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Blocks misconfigured items")
        void purchaseItem_invalidConfiguration() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(shopItemRepository.findById(2L)).thenReturn(Optional.of(emoticonItem));
            when(shopEntitlementCapabilityRegistry.supports(emoticonItem)).thenReturn(true);
            doThrow(new IllegalStateException("missing-target"))
                    .when(shopEntitlementCapabilityRegistry)
                    .validateConfiguration(emoticonItem);

            assertThatThrownBy(() -> shopService.purchaseItem(1L, 2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITEM_NOT_AVAILABLE);

            verify(pointService, never()).spendPoint(anyLong(), anyInt(), anyString(), anyLong(), anyString());
            verify(shopEntitlementCapabilityRegistry, never()).grant(anyLong(), any());
        }

        @Test
        @DisplayName("Blocks preflight failures before spending points")
        void purchaseItem_preflightFailure_doesNotSpendPoints() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(shopItemRepository.findById(2L)).thenReturn(Optional.of(emoticonItem));
            when(shopEntitlementCapabilityRegistry.supports(emoticonItem)).thenReturn(true);
            doThrow(new BusinessException(ErrorCode.EMOTICON_ALREADY_PURCHASED))
                    .when(shopEntitlementCapabilityRegistry)
                    .preflightPurchase(1L, emoticonItem);

            assertThatThrownBy(() -> shopService.purchaseItem(1L, 2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.EMOTICON_ALREADY_PURCHASED);

            verify(pointService, never()).spendPoint(anyLong(), anyInt(), anyString(), anyLong(), anyString());
            verify(shopEntitlementCapabilityRegistry, never()).grant(anyLong(), any());
            verify(purchaseHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Propagates grant failure without saving purchase history")
        void purchaseItem_grantFailure() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(shopItemRepository.findById(2L)).thenReturn(Optional.of(emoticonItem));
            when(shopEntitlementCapabilityRegistry.supports(emoticonItem)).thenReturn(true);
            org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.EMOTICON_ALREADY_PURCHASED))
                    .when(shopEntitlementCapabilityRegistry)
                    .grant(1L, emoticonItem);

            assertThatThrownBy(() -> shopService.purchaseItem(1L, 2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.EMOTICON_ALREADY_PURCHASED);

            verify(pointService).spendPoint(
                    eq(1L),
                    eq(100),
                    eq("Shop item purchase: Premium emoticon"),
                    eq(2L),
                    eq("SHOP_ITEM"));
            verify(purchaseHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Fails for a missing item")
        void purchaseItem_notFound() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(shopItemRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shopService.purchaseItem(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITEM_NOT_AVAILABLE);
        }

        @Test
        @DisplayName("Fails for a missing user")
        void purchaseItem_userNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shopService.purchaseItem(999L, 2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("Blocks banned users")
        void purchaseItem_bannedUser() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE))
                    .when(sanctionService)
                    .validateNotBanned(user);

            assertThatThrownBy(() -> shopService.purchaseItem(1L, 2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_ACTIVE);

            verify(shopItemRepository, never()).findById(anyLong());
            verify(pointService, never()).spendPoint(anyLong(), anyInt(), anyString(), anyLong(), anyString());
        }
    }

    @Test
    @DisplayName("Returns shop item details")
    void getShopItem_success() {
        when(shopItemRepository.findById(2L)).thenReturn(Optional.of(emoticonItem));

        ShopItem result = shopService.getShopItem(2L);

        assertThat(result.getItemName()).isEqualTo("Premium emoticon");
        assertThat(result.getItemType()).isEqualTo("EMOTICON");
    }

    @Test
    @DisplayName("Fails for a missing shop item detail")
    void getShopItem_notFound() {
        when(shopItemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.getShopItem(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_NOT_AVAILABLE);
    }
}
