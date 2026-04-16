package com.weedrice.whiteboard.domain.shop.service;

import com.weedrice.whiteboard.domain.point.service.PointService;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShopService 테스트")
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
                .itemName("프리미엄 이모티콘")
                .description("테스트 상품")
                .price(100)
                .itemType("EMOTICON")
                .imageUrl("https://example.com/emoticon.png")
                .build();
        ReflectionTestUtils.setField(emoticonItem, "itemId", 2L);
        ReflectionTestUtils.setField(emoticonItem, "isActive", true);

        decorationItem = ShopItem.builder()
                .itemName("프로필 장식")
                .description("장식 상품")
                .price(500)
                .itemType("DECORATION")
                .build();
        ReflectionTestUtils.setField(decorationItem, "itemId", 3L);
        ReflectionTestUtils.setField(decorationItem, "isActive", true);
    }

    @Nested
    @DisplayName("상품 목록 조회")
    class GetShopItems {

        @Test
        @DisplayName("지원되는 타입은 조회한다")
        void getShopItems_supportedType() {
            Pageable pageable = PageRequest.of(0, 20);
            when(shopEntitlementCapabilityRegistry.supports("EMOTICON")).thenReturn(true);
            when(shopEntitlementCapabilityRegistry.getSupportedItemTypes()).thenReturn(Set.of("EMOTICON"));
            when(shopItemRepository.findByIsActiveAndItemType(true, "EMOTICON", pageable))
                    .thenReturn(new PageImpl<>(List.of(emoticonItem), pageable, 1));

            ShopItemResponse response = shopService.getShopItems("EMOTICON", pageable);

            assertThat(response.getContent()).hasSize(1);
            verify(shopItemRepository).findByIsActiveAndItemType(true, "EMOTICON", pageable);
        }

        @Test
        @DisplayName("지원 가능한 handler가 없으면 빈 결과를 반환한다")
        void getShopItems_withoutSupportedHandlers_returnsEmpty() {
            Pageable pageable = PageRequest.of(0, 20);
            when(shopEntitlementCapabilityRegistry.getSupportedItemTypes()).thenReturn(Set.of());

            ShopItemResponse response = shopService.getShopItems("", pageable);

            assertThat(response.getContent()).isEmpty();
            verify(shopItemRepository, never()).findByIsActive(any(), any());
            verify(shopItemRepository, never()).findByIsActiveAndItemTypeIn(any(), any(), any());
        }

        @Test
        @DisplayName("전체 조회는 지원 가능한 타입만 조회한다")
        void getShopItems_allTypes_usesSupportedTypes() {
            Pageable pageable = PageRequest.of(0, 20);
            Set<String> supportedTypes = Set.of("EMOTICON", "DECORATION");
            when(shopEntitlementCapabilityRegistry.getSupportedItemTypes()).thenReturn(supportedTypes);
            when(shopItemRepository.findByIsActiveAndItemTypeIn(true, supportedTypes, pageable))
                    .thenReturn(new PageImpl<>(List.of(emoticonItem, decorationItem), pageable, 2));

            ShopItemResponse response = shopService.getShopItems(null, pageable);

            assertThat(response.getContent()).hasSize(2);
            verify(shopItemRepository).findByIsActiveAndItemTypeIn(true, supportedTypes, pageable);
        }

        @Test
        @DisplayName("지원되지 않는 타입 요청은 빈 결과를 반환한다")
        void getShopItems_unsupportedType_returnsEmpty() {
            Pageable pageable = PageRequest.of(0, 20);
            when(shopEntitlementCapabilityRegistry.supports("EMOTICON")).thenReturn(false);

            ShopItemResponse response = shopService.getShopItems("EMOTICON", pageable);

            assertThat(response.getContent()).isEmpty();
            verify(shopItemRepository, never()).findByIsActiveAndItemType(any(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("상품 구매")
    class PurchaseItem {

        @Test
        @DisplayName("지원되는 상품은 구매할 수 있다")
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
            verify(pointService).spendPoint(
                    eq(1L),
                    eq(100),
                    eq("프리미엄 이모티콘 구매"),
                    eq(2L),
                    eq("SHOP_ITEM"));
            verify(purchaseHistoryRepository).save(any(PurchaseHistory.class));
        }

        @Test
        @DisplayName("비활성 상품 구매는 차단한다")
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
        @DisplayName("지원되지 않는 상품 구매는 차단한다")
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
        @DisplayName("존재하지 않는 상품 구매는 실패한다")
        void purchaseItem_notFound() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(shopItemRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shopService.purchaseItem(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITEM_NOT_AVAILABLE);
        }

        @Test
        @DisplayName("존재하지 않는 사용자 구매는 실패한다")
        void purchaseItem_userNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shopService.purchaseItem(999L, 2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Test
    @DisplayName("상품 상세 조회는 기존처럼 유지한다")
    void getShopItem_success() {
        when(shopItemRepository.findById(2L)).thenReturn(Optional.of(emoticonItem));

        ShopItem result = shopService.getShopItem(2L);

        assertThat(result.getItemName()).isEqualTo("프리미엄 이모티콘");
        assertThat(result.getItemType()).isEqualTo("EMOTICON");
    }

    @Test
    @DisplayName("존재하지 않는 상품 상세 조회는 실패한다")
    void getShopItem_notFound() {
        when(shopItemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.getShopItem(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_NOT_AVAILABLE);
    }
}
