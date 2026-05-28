package com.weedrice.whiteboard.domain.shop.service;

import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.shop.dto.PurchaseHistoryResponse;
import com.weedrice.whiteboard.domain.shop.dto.ShopItemResponse;
import com.weedrice.whiteboard.domain.shop.entity.PurchaseHistory;
import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import com.weedrice.whiteboard.domain.shop.repository.PurchaseHistoryRepository;
import com.weedrice.whiteboard.domain.shop.repository.ShopItemRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserReadableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private ShopEntitlementHandler shopEntitlementHandler;
    @Mock
    private ShopEntitlementCapabilityRegistry shopEntitlementCapabilityRegistry;
    @Mock
    private SanctionService sanctionService;
    @Mock
    private UserReadableResolver userReadableResolver;

    @InjectMocks
    private ShopService shopService;

    private User user;
    private ShopItem emoticonItem;
    private ShopItem decorationItem;
    private ShopEntitlementCapabilityRegistry.PreparedPurchase preparedPurchase;

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

        preparedPurchase = new ShopEntitlementCapabilityRegistry.PreparedPurchase(
                shopEntitlementHandler,
                TestPurchasePreparation.INSTANCE);
    }

    @Nested
    @DisplayName("Get shop items")
    class GetShopItems {

        @Test
        @DisplayName("Returns a supported item type")
        void getShopItems_supportedType() {
            Pageable pageable = PageRequest.of(0, 20);
            Pageable expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Order.asc("itemId")));
            when(shopEntitlementCapabilityRegistry.getSupportedItemTypes()).thenReturn(Set.of("EMOTICON"));
            when(shopEntitlementCapabilityRegistry.supports("EMOTICON")).thenReturn(true);
            when(shopItemRepository.findByIsActiveAndItemType(true, "EMOTICON", expectedPageable))
                    .thenReturn(new PageImpl<>(List.of(emoticonItem), expectedPageable, 1));

            ShopItemResponse response = shopService.getShopItems("EMOTICON", pageable);

            assertThat(response.getContent()).hasSize(1);
            verify(shopItemRepository).findByIsActiveAndItemType(true, "EMOTICON", expectedPageable);
        }

        @Test
        @DisplayName("Normalizes a supported item type filter")
        void getShopItems_supportedType_normalizesFilter() {
            Pageable pageable = PageRequest.of(0, 20);
            Pageable expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Order.asc("itemId")));
            when(shopEntitlementCapabilityRegistry.getSupportedItemTypes()).thenReturn(Set.of("EMOTICON"));
            when(shopEntitlementCapabilityRegistry.supports("EMOTICON")).thenReturn(true);
            when(shopItemRepository.findByIsActiveAndItemType(true, "EMOTICON", expectedPageable))
                    .thenReturn(new PageImpl<>(List.of(emoticonItem), expectedPageable, 1));

            ShopItemResponse response = shopService.getShopItems(" emoticon ", pageable);

            assertThat(response.getContent()).hasSize(1);
            verify(shopEntitlementCapabilityRegistry).supports("EMOTICON");
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
        @DisplayName("Treats a blank item type filter as absent")
        void getShopItems_blankType_usesSupportedTypes() {
            Pageable pageable = PageRequest.of(0, 20);
            Pageable expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Order.asc("itemId")));
            Set<String> supportedTypes = Set.of("EMOTICON", "DECORATION");
            when(shopEntitlementCapabilityRegistry.getSupportedItemTypes()).thenReturn(supportedTypes);
            when(shopItemRepository.findByIsActiveAndItemTypeIn(true, supportedTypes, expectedPageable))
                    .thenReturn(new PageImpl<>(List.of(emoticonItem, decorationItem), expectedPageable, 2));

            ShopItemResponse response = shopService.getShopItems("   ", pageable);

            assertThat(response.getContent()).hasSize(2);
            verify(shopEntitlementCapabilityRegistry, never()).supports(anyString());
            verify(shopItemRepository).findByIsActiveAndItemTypeIn(true, supportedTypes, expectedPageable);
        }

        @Test
        @DisplayName("Returns empty for a type filter when there is no supported handler")
        void getShopItems_withoutSupportedHandlersAndType_returnsEmpty() {
            Pageable pageable = PageRequest.of(0, 20);
            when(shopEntitlementCapabilityRegistry.getSupportedItemTypes()).thenReturn(Set.of());

            ShopItemResponse response = shopService.getShopItems("BADGE", pageable);

            assertThat(response.getContent()).isEmpty();
            verify(shopEntitlementCapabilityRegistry, never()).supports(anyString());
            verify(shopItemRepository, never()).findByIsActiveAndItemType(any(), anyString(), any());
        }

        @Test
        @DisplayName("Rejects an unsupported type filter")
        void getShopItems_unsupportedType_throwsInvalidInput() {
            Pageable pageable = PageRequest.of(0, 20);
            when(shopEntitlementCapabilityRegistry.getSupportedItemTypes()).thenReturn(Set.of("EMOTICON"));
            when(shopEntitlementCapabilityRegistry.supports("BADGE")).thenReturn(false);

            assertThatThrownBy(() -> shopService.getShopItems("BADGE", pageable))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

            verify(shopItemRepository, never()).findByIsActiveAndItemType(any(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("Purchase item")
    class PurchaseItem {

        @Test
        @DisplayName("Purchases a supported item and grants the entitlement")
        void purchaseItem_success() {
            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
            when(shopItemRepository.findById(2L)).thenReturn(Optional.of(emoticonItem));
            when(shopEntitlementCapabilityRegistry.supports(emoticonItem)).thenReturn(true);
            when(shopEntitlementCapabilityRegistry.preparePurchase(user, emoticonItem)).thenReturn(preparedPurchase);

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
            inOrder.verify(shopEntitlementCapabilityRegistry).preparePurchase(user, emoticonItem);
            inOrder.verify(pointService).spendPointForPrevalidatedUser(
                    eq(user),
                    eq(100),
                    eq("Shop item purchase: Premium emoticon"),
                    eq(2L),
                    eq("SHOP_ITEM"));
            inOrder.verify(shopEntitlementCapabilityRegistry).grant(preparedPurchase);
            inOrder.verify(purchaseHistoryRepository).save(any(PurchaseHistory.class));
        }

        @Test
        @DisplayName("Purchases an active item by type and target without itemId lookup")
        void purchaseActiveItemByTarget_success() {
            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
            when(shopItemRepository.findTop2ByIsActiveAndItemTypeAndTargetId(true, "EMOTICON", 10L))
                    .thenReturn(List.of(emoticonItem));
            when(shopEntitlementCapabilityRegistry.supports(emoticonItem)).thenReturn(true);
            when(shopEntitlementCapabilityRegistry.supportsValidatedPurchasePreparation(emoticonItem)).thenReturn(true);
            when(shopEntitlementCapabilityRegistry.prepareValidatedPurchase(eq(user), eq(emoticonItem), any(Runnable.class)))
                    .thenReturn(preparedPurchase);
            PurchaseHistory savedPurchaseHistory = PurchaseHistory.builder()
                    .user(user)
                    .item(emoticonItem)
                    .purchasedPrice(emoticonItem.getPrice())
                    .build();
            ReflectionTestUtils.setField(savedPurchaseHistory, "purchaseId", 1L);
            when(purchaseHistoryRepository.save(any(PurchaseHistory.class))).thenReturn(savedPurchaseHistory);

            Long purchaseId = shopService.purchaseActiveItemByTarget(1L, " emoticon ", 10L);

            assertThat(purchaseId).isEqualTo(1L);
            verify(shopItemRepository, never()).findById(anyLong());
            verify(shopItemRepository).findTop2ByIsActiveAndItemTypeAndTargetId(true, "EMOTICON", 10L);
            verify(shopEntitlementCapabilityRegistry, never()).validateConfiguration(any());
            verify(shopEntitlementCapabilityRegistry, never()).preparePurchase(anyLong(), any());
            verify(shopEntitlementCapabilityRegistry, never()).preparePurchase(any(User.class), any(ShopItem.class));
            verify(shopEntitlementCapabilityRegistry).grant(preparedPurchase);
        }

        @Test
        @DisplayName("Rejects active target purchases when item configuration is not unique")
        void purchaseActiveItemByTarget_nonUniqueItem_throwsItemNotAvailable() {
            ShopItem duplicate = ShopItem.builder()
                    .itemName("Duplicate")
                    .price(100)
                    .itemType("EMOTICON")
                    .targetId(10L)
                    .build();
            when(shopItemRepository.findTop2ByIsActiveAndItemTypeAndTargetId(true, "EMOTICON", 10L))
                    .thenReturn(List.of(emoticonItem, duplicate));

            assertThatThrownBy(() -> shopService.purchaseActiveItemByTarget(1L, "EMOTICON", 10L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITEM_NOT_AVAILABLE);

            verify(userRepository, never()).findByIdForUpdate(anyLong());
            verify(shopEntitlementCapabilityRegistry, never()).preparePurchase(anyLong(), any());
            verify(shopEntitlementCapabilityRegistry, never()).preparePurchase(any(User.class), any(ShopItem.class));
            verify(shopEntitlementCapabilityRegistry, never()).prepareValidatedPurchase(anyLong(), any(), any());
            verify(shopEntitlementCapabilityRegistry, never()).prepareValidatedPurchase(
                    any(User.class),
                    any(ShopItem.class),
                    any(Runnable.class));
            verifyNoInteractions(sanctionService);
            verifyNoInteractions(pointService);
            verify(purchaseHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rejects active target purchases when no active item exists before locking the user")
        void purchaseActiveItemByTarget_missingItem_throwsItemNotAvailableBeforeUserLock() {
            when(shopItemRepository.findTop2ByIsActiveAndItemTypeAndTargetId(true, "EMOTICON", 10L))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> shopService.purchaseActiveItemByTarget(1L, "EMOTICON", 10L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITEM_NOT_AVAILABLE);

            verify(userRepository, never()).findByIdForUpdate(anyLong());
            verifyNoInteractions(sanctionService);
            verifyNoInteractions(pointService);
            verify(shopEntitlementCapabilityRegistry, never()).preparePurchase(any(User.class), any(ShopItem.class));
            verify(shopEntitlementCapabilityRegistry, never()).prepareValidatedPurchase(
                    any(User.class),
                    any(ShopItem.class),
                    any(Runnable.class));
            verify(purchaseHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Blocks banned users after resolving a unique active target item")
        void purchaseActiveItemByTarget_bannedUser_throwsUserNotActive() {
            when(shopItemRepository.findTop2ByIsActiveAndItemTypeAndTargetId(true, "EMOTICON", 10L))
                    .thenReturn(List.of(emoticonItem));
            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
            doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE))
                    .when(sanctionService)
                    .validateNotBanned(user);

            assertThatThrownBy(() -> shopService.purchaseActiveItemByTarget(1L, "EMOTICON", 10L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_ACTIVE);

            verify(shopEntitlementCapabilityRegistry, never()).preparePurchase(anyLong(), any());
            verify(shopEntitlementCapabilityRegistry, never()).preparePurchase(any(User.class), any(ShopItem.class));
            verify(shopEntitlementCapabilityRegistry, never()).prepareValidatedPurchase(anyLong(), any(), any());
            verify(shopEntitlementCapabilityRegistry, never()).prepareValidatedPurchase(
                    any(User.class),
                    any(ShopItem.class),
                    any(Runnable.class));
            verifyNoInteractions(pointService);
            verify(purchaseHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Free items grant entitlement and save purchase history without spending points")
        void purchaseItem_freeItem_skipsPointSpending() {
            ShopItem freeEmoticonItem = ShopItem.builder()
                    .itemName("Free emoticon")
                    .description("Free shop item")
                    .price(0)
                    .itemType("EMOTICON")
                    .targetId(11L)
                    .imageUrl("https://example.com/free-emoticon.png")
                    .build();
            ReflectionTestUtils.setField(freeEmoticonItem, "itemId", 4L);
            ReflectionTestUtils.setField(freeEmoticonItem, "isActive", true);
            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
            when(shopItemRepository.findById(4L)).thenReturn(Optional.of(freeEmoticonItem));
            when(shopEntitlementCapabilityRegistry.supports(freeEmoticonItem)).thenReturn(true);
            when(shopEntitlementCapabilityRegistry.preparePurchase(user, freeEmoticonItem)).thenReturn(preparedPurchase);

            PurchaseHistory savedPurchaseHistory = PurchaseHistory.builder()
                    .user(user)
                    .item(freeEmoticonItem)
                    .purchasedPrice(0)
                    .build();
            ReflectionTestUtils.setField(savedPurchaseHistory, "purchaseId", 4L);
            when(purchaseHistoryRepository.save(any(PurchaseHistory.class))).thenReturn(savedPurchaseHistory);

            Long purchaseId = shopService.purchaseItem(1L, 4L);

            assertThat(purchaseId).isEqualTo(4L);
            verifyNoInteractions(pointService);
            InOrder inOrder = inOrder(sanctionService, shopEntitlementCapabilityRegistry, purchaseHistoryRepository);
            inOrder.verify(sanctionService).validateNotBanned(user);
            inOrder.verify(shopEntitlementCapabilityRegistry).validateConfiguration(freeEmoticonItem);
            inOrder.verify(shopEntitlementCapabilityRegistry).preparePurchase(user, freeEmoticonItem);
            inOrder.verify(shopEntitlementCapabilityRegistry).grant(preparedPurchase);
            ArgumentCaptor<PurchaseHistory> historyCaptor = ArgumentCaptor.forClass(PurchaseHistory.class);
            inOrder.verify(purchaseHistoryRepository).save(historyCaptor.capture());
            assertThat(historyCaptor.getValue().getPurchasedPrice()).isZero();
            assertThat(historyCaptor.getValue().getItem()).isSameAs(freeEmoticonItem);
        }

        @Test
        @DisplayName("Rejects negative price items before preparing entitlement")
        void purchaseItem_negativePrice_throwsInvalidInput() {
            ShopItem negativePriceItem = ShopItem.builder()
                    .itemName("Invalid item")
                    .description("Invalid shop item")
                    .price(-1)
                    .itemType("EMOTICON")
                    .targetId(12L)
                    .build();
            ReflectionTestUtils.setField(negativePriceItem, "itemId", 5L);
            ReflectionTestUtils.setField(negativePriceItem, "isActive", true);
            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
            when(shopItemRepository.findById(5L)).thenReturn(Optional.of(negativePriceItem));
            when(shopEntitlementCapabilityRegistry.supports(negativePriceItem)).thenReturn(true);

            assertThatThrownBy(() -> shopService.purchaseItem(1L, 5L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

            verify(shopEntitlementCapabilityRegistry, never()).preparePurchase(anyLong(), any());
            verify(shopEntitlementCapabilityRegistry, never()).preparePurchase(any(User.class), any(ShopItem.class));
            verifyNoInteractions(pointService);
            verify(shopEntitlementCapabilityRegistry, never()).grant(any());
            verify(purchaseHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rejects negative price items after combined configuration validation")
        void purchaseItem_combinedPreparationNegativePrice_throwsInvalidInput() {
            ShopItem negativePriceItem = ShopItem.builder()
                    .itemName("Invalid item")
                    .description("Invalid shop item")
                    .price(-1)
                    .itemType("EMOTICON")
                    .targetId(12L)
                    .build();
            ReflectionTestUtils.setField(negativePriceItem, "itemId", 5L);
            ReflectionTestUtils.setField(negativePriceItem, "isActive", true);
            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
            when(shopItemRepository.findById(5L)).thenReturn(Optional.of(negativePriceItem));
            when(shopEntitlementCapabilityRegistry.supports(negativePriceItem)).thenReturn(true);
            when(shopEntitlementCapabilityRegistry.supportsValidatedPurchasePreparation(negativePriceItem))
                    .thenReturn(true);
            when(shopEntitlementCapabilityRegistry.prepareValidatedPurchase(
                    eq(user),
                    eq(negativePriceItem),
                    any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        Runnable validatePrice = invocation.getArgument(2);
                        validatePrice.run();
                        return preparedPurchase;
                    });

            assertThatThrownBy(() -> shopService.purchaseItem(1L, 5L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

            verifyNoInteractions(pointService);
            verify(shopEntitlementCapabilityRegistry, never()).grant(any());
            verify(purchaseHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Prioritizes combined configuration failures over negative price")
        void purchaseItem_combinedPreparationInvalidConfigurationBeforeNegativePrice() {
            ShopItem invalidConfigurationItem = ShopItem.builder()
                    .itemName("Invalid item")
                    .description("Invalid shop item")
                    .price(-1)
                    .itemType("EMOTICON")
                    .targetId(12L)
                    .build();
            ReflectionTestUtils.setField(invalidConfigurationItem, "itemId", 5L);
            ReflectionTestUtils.setField(invalidConfigurationItem, "isActive", true);
            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
            when(shopItemRepository.findById(5L)).thenReturn(Optional.of(invalidConfigurationItem));
            when(shopEntitlementCapabilityRegistry.supports(invalidConfigurationItem)).thenReturn(true);
            when(shopEntitlementCapabilityRegistry.supportsValidatedPurchasePreparation(invalidConfigurationItem))
                    .thenReturn(true);
            when(shopEntitlementCapabilityRegistry.prepareValidatedPurchase(
                    eq(user),
                    eq(invalidConfigurationItem),
                    any(Runnable.class)))
                    .thenThrow(new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE));

            assertThatThrownBy(() -> shopService.purchaseItem(1L, 5L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITEM_NOT_AVAILABLE);

            verifyNoInteractions(pointService);
            verify(shopEntitlementCapabilityRegistry, never()).grant(any());
            verify(purchaseHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Blocks inactive items")
        void purchaseItem_inactiveItem() {
            ReflectionTestUtils.setField(emoticonItem, "isActive", false);
            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
            when(shopItemRepository.findById(2L)).thenReturn(Optional.of(emoticonItem));

            assertThatThrownBy(() -> shopService.purchaseItem(1L, 2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITEM_NOT_AVAILABLE);

            verifyNoInteractions(pointService);
            verify(purchaseHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Blocks unsupported items")
        void purchaseItem_unsupportedItem() {
            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
            when(shopItemRepository.findById(2L)).thenReturn(Optional.of(emoticonItem));
            when(shopEntitlementCapabilityRegistry.supports(emoticonItem)).thenReturn(false);

            assertThatThrownBy(() -> shopService.purchaseItem(1L, 2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITEM_NOT_AVAILABLE);

            verifyNoInteractions(pointService);
            verify(purchaseHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Blocks misconfigured items")
        void purchaseItem_invalidConfiguration() {
            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
            when(shopItemRepository.findById(2L)).thenReturn(Optional.of(emoticonItem));
            when(shopEntitlementCapabilityRegistry.supports(emoticonItem)).thenReturn(true);
            doThrow(new IllegalStateException("missing-target"))
                    .when(shopEntitlementCapabilityRegistry)
                    .validateConfiguration(emoticonItem);

            assertThatThrownBy(() -> shopService.purchaseItem(1L, 2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITEM_NOT_AVAILABLE);

            verifyNoInteractions(pointService);
            verify(shopEntitlementCapabilityRegistry, never()).preparePurchase(anyLong(), any());
            verify(shopEntitlementCapabilityRegistry, never()).preparePurchase(any(User.class), any(ShopItem.class));
            verify(shopEntitlementCapabilityRegistry, never()).grant(any());
        }

        @Test
        @DisplayName("Blocks preflight failures before spending points")
        void purchaseItem_preflightFailure_doesNotSpendPoints() {
            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
            when(shopItemRepository.findById(2L)).thenReturn(Optional.of(emoticonItem));
            when(shopEntitlementCapabilityRegistry.supports(emoticonItem)).thenReturn(true);
            doThrow(new BusinessException(ErrorCode.EMOTICON_ALREADY_PURCHASED))
                    .when(shopEntitlementCapabilityRegistry)
                    .preparePurchase(user, emoticonItem);

            assertThatThrownBy(() -> shopService.purchaseItem(1L, 2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.EMOTICON_ALREADY_PURCHASED);

            verifyNoInteractions(pointService);
            verify(shopEntitlementCapabilityRegistry, never()).grant(any());
            verify(purchaseHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Propagates grant failure without saving purchase history")
        void purchaseItem_grantFailure() {
            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
            when(shopItemRepository.findById(2L)).thenReturn(Optional.of(emoticonItem));
            when(shopEntitlementCapabilityRegistry.supports(emoticonItem)).thenReturn(true);
            when(shopEntitlementCapabilityRegistry.preparePurchase(user, emoticonItem)).thenReturn(preparedPurchase);
            doThrow(new BusinessException(ErrorCode.EMOTICON_ALREADY_PURCHASED))
                    .when(shopEntitlementCapabilityRegistry)
                    .grant(preparedPurchase);

            assertThatThrownBy(() -> shopService.purchaseItem(1L, 2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.EMOTICON_ALREADY_PURCHASED);

            verify(pointService).spendPointForPrevalidatedUser(
                    eq(user),
                    eq(100),
                    eq("Shop item purchase: Premium emoticon"),
                    eq(2L),
                    eq("SHOP_ITEM"));
            verify(purchaseHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Fails for a missing item")
        void purchaseItem_notFound() {
            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
            when(shopItemRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shopService.purchaseItem(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITEM_NOT_AVAILABLE);
        }

        @Test
        @DisplayName("Fails for a missing user")
        void purchaseItem_userNotFound() {
            when(userRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shopService.purchaseItem(999L, 2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("Blocks banned users")
        void purchaseItem_bannedUser() {
            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
            doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE))
                    .when(sanctionService)
                    .validateNotBanned(user);

            assertThatThrownBy(() -> shopService.purchaseItem(1L, 2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_ACTIVE);

            verify(shopItemRepository, never()).findById(anyLong());
            verifyNoInteractions(pointService);
        }
    }

    @Test
    @DisplayName("Purchase histories include the purchased item image URL")
    void getPurchaseHistories_mapsItemImageUrl() {
        PurchaseHistory purchaseHistory = PurchaseHistory.builder()
                .user(user)
                .item(emoticonItem)
                .purchasedPrice(emoticonItem.getPrice())
                .build();
        ReflectionTestUtils.setField(purchaseHistory, "purchaseId", 10L);
        Pageable pageable = PageRequest.of(0, 20);
        Pageable expectedPageable = PageRequest.of(0, 20, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("purchaseId")));
        when(purchaseHistoryRepository.findByUser_UserIdOrderByCreatedAtDescPurchaseIdDesc(1L, expectedPageable))
                .thenReturn(new PageImpl<>(List.of(purchaseHistory), expectedPageable, 1));

        PurchaseHistoryResponse response = shopService.getPurchaseHistories(1L, pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getItem().getImageUrl())
                .isEqualTo("https://example.com/emoticon.png");
    }

    @Test
    @DisplayName("Purchase histories limit page size and sort fields")
    void getPurchaseHistories_normalizesPageable() {
        Pageable requested = PageRequest.of(2, 250, Sort.by(Sort.Order.asc("itemName")));
        Pageable expectedPageable = PageRequest.of(2, 100, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("purchaseId")));
        when(purchaseHistoryRepository.findByUser_UserIdOrderByCreatedAtDescPurchaseIdDesc(1L, expectedPageable))
                .thenReturn(new PageImpl<>(List.of(), expectedPageable, 0));

        PurchaseHistoryResponse response = shopService.getPurchaseHistories(1L, requested);

        assertThat(response.getContent()).isEmpty();
        verify(purchaseHistoryRepository).findByUser_UserIdOrderByCreatedAtDescPurchaseIdDesc(1L, expectedPageable);
        verify(userReadableResolver).ensureExists(1L);
    }

    @Test
    @DisplayName("Purchase histories preserve USER_NOT_FOUND for a missing user")
    void getPurchaseHistories_missingUser_throwsUserNotFound() {
        Pageable requested = PageRequest.of(0, 20);
        Pageable expectedPageable = PageRequest.of(0, 20, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("purchaseId")));
        when(purchaseHistoryRepository.findByUser_UserIdOrderByCreatedAtDescPurchaseIdDesc(999L, expectedPageable))
                .thenReturn(new PageImpl<>(List.of(), expectedPageable, 0));
        doThrow(new BusinessException(ErrorCode.USER_NOT_FOUND))
                .when(userReadableResolver).ensureExists(999L);

        assertThatThrownBy(() -> shopService.getPurchaseHistories(999L, requested))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
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

    private enum TestPurchasePreparation implements ShopEntitlementHandler.PurchasePreparation {
        INSTANCE
    }

}
