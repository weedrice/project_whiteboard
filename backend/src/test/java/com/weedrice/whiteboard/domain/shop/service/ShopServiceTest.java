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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

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

    @InjectMocks
    private ShopService shopService;

    private User user;
    private ShopItem shopItem;
    private ShopItem emoticonItem;
    private ShopItem decorationItem;

    @BeforeEach
    void setUp() {
        user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        shopItem = ShopItem.builder()
                .itemName("Test Item")
                .price(100)
                .build();
        ReflectionTestUtils.setField(shopItem, "itemId", 1L);
        ReflectionTestUtils.setField(shopItem, "isActive", true);

        emoticonItem = ShopItem.builder()
                .itemName("프리미엄 이모티콘")
                .description("특별한 이모티콘 세트")
                .price(100)
                .itemType("EMOTICON")
                .imageUrl("https://example.com/emoticon.png")
                .build();
        ReflectionTestUtils.setField(emoticonItem, "itemId", 2L);
        ReflectionTestUtils.setField(emoticonItem, "isActive", true);

        decorationItem = ShopItem.builder()
                .itemName("닉네임 색상")
                .price(500)
                .itemType("DECORATION")
                .build();
        ReflectionTestUtils.setField(decorationItem, "itemId", 3L);
        ReflectionTestUtils.setField(decorationItem, "isActive", true);
    }

    @Test
    @DisplayName("아이템 구매 성공")
    void purchaseItem_success() {
        // given
        Long userId = 1L;
        Long itemId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(shopItemRepository.findById(itemId)).thenReturn(Optional.of(shopItem));
        PurchaseHistory savedPurchaseHistory = PurchaseHistory.builder()
                .user(user)
                .item(shopItem)
                .purchasedPrice(shopItem.getPrice())
                .build();
        ReflectionTestUtils.setField(savedPurchaseHistory, "purchaseId", 1L);
        when(purchaseHistoryRepository.save(any(PurchaseHistory.class))).thenReturn(savedPurchaseHistory);

        // when
        Long purchaseId = shopService.purchaseItem(userId, itemId);

        // then
        assertThat(purchaseId).isNotNull();
        verify(pointService).spendPoint(anyLong(), anyInt(), anyString(), anyLong(), anyString());
        verify(purchaseHistoryRepository).save(any(PurchaseHistory.class));
    }

    @Nested
    @DisplayName("EMOTICON 아이템 조회")
    class GetEmoticonItems {

        @Test
        @DisplayName("EMOTICON 타입 아이템 목록 조회 성공")
        void getShopItems_emoticonType() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            ShopItem emoticonItem2 = ShopItem.builder()
                    .itemName("동물 이모티콘")
                    .price(150)
                    .itemType("EMOTICON")
                    .build();
            ReflectionTestUtils.setField(emoticonItem2, "itemId", 4L);
            ReflectionTestUtils.setField(emoticonItem2, "isActive", true);

            Page<ShopItem> emoticonPage = new PageImpl<>(
                    Arrays.asList(emoticonItem, emoticonItem2),
                    pageable,
                    2
            );
            when(shopItemRepository.findByIsActiveAndItemType(true, "EMOTICON", pageable))
                    .thenReturn(emoticonPage);

            // when
            ShopItemResponse response = shopService.getShopItems("EMOTICON", pageable);

            // then
            assertThat(response).isNotNull();
            verify(shopItemRepository).findByIsActiveAndItemType(true, "EMOTICON", pageable);
            verify(shopItemRepository, never()).findByIsActive(any(Boolean.class), any(Pageable.class));
        }

        @Test
        @DisplayName("타입 지정 없이 전체 아이템 조회")
        void getShopItems_allTypes() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            Page<ShopItem> allItemsPage = new PageImpl<>(
                    Arrays.asList(emoticonItem, decorationItem),
                    pageable,
                    2
            );
            when(shopItemRepository.findByIsActive(true, pageable)).thenReturn(allItemsPage);

            // when
            ShopItemResponse response = shopService.getShopItems(null, pageable);

            // then
            assertThat(response).isNotNull();
            verify(shopItemRepository).findByIsActive(true, pageable);
            verify(shopItemRepository, never()).findByIsActiveAndItemType(any(), any(), any());
        }

        @Test
        @DisplayName("빈 타입 문자열로 전체 아이템 조회")
        void getShopItems_emptyTypeString() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            Page<ShopItem> allItemsPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
            when(shopItemRepository.findByIsActive(true, pageable)).thenReturn(allItemsPage);

            // when
            ShopItemResponse response = shopService.getShopItems("", pageable);

            // then
            assertThat(response).isNotNull();
            verify(shopItemRepository).findByIsActive(true, pageable);
        }
    }

    @Nested
    @DisplayName("EMOTICON 아이템 구매")
    class PurchaseEmoticonItem {

        @Test
        @DisplayName("EMOTICON 아이템 구매 성공")
        void purchaseEmoticonItem_success() {
            // given
            Long userId = 1L;
            Long itemId = 2L;
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(shopItemRepository.findById(itemId)).thenReturn(Optional.of(emoticonItem));

            PurchaseHistory savedPurchaseHistory = PurchaseHistory.builder()
                    .user(user)
                    .item(emoticonItem)
                    .purchasedPrice(emoticonItem.getPrice())
                    .build();
            ReflectionTestUtils.setField(savedPurchaseHistory, "purchaseId", 1L);
            when(purchaseHistoryRepository.save(any(PurchaseHistory.class))).thenReturn(savedPurchaseHistory);

            // when
            Long purchaseId = shopService.purchaseItem(userId, itemId);

            // then
            assertThat(purchaseId).isEqualTo(1L);
            verify(pointService).spendPoint(
                    eq(userId),
                    eq(100),
                    eq("프리미엄 이모티콘 구매"),
                    eq(2L),
                    eq("SHOP_ITEM")
            );
            verify(purchaseHistoryRepository).save(any(PurchaseHistory.class));
        }

        @Test
        @DisplayName("비활성화된 EMOTICON 아이템 구매 실패")
        void purchaseEmoticonItem_inactiveItem() {
            // given
            Long userId = 1L;
            Long itemId = 2L;
            ReflectionTestUtils.setField(emoticonItem, "isActive", false);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(shopItemRepository.findById(itemId)).thenReturn(Optional.of(emoticonItem));

            // when & then
            assertThatThrownBy(() -> shopService.purchaseItem(userId, itemId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITEM_NOT_AVAILABLE);

            verify(purchaseHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("존재하지 않는 EMOTICON 아이템 구매 실패")
        void purchaseEmoticonItem_notFound() {
            // given
            Long userId = 1L;
            Long itemId = 999L;
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(shopItemRepository.findById(itemId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> shopService.purchaseItem(userId, itemId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITEM_NOT_AVAILABLE);
        }

        @Test
        @DisplayName("존재하지 않는 사용자 - EMOTICON 구매 실패")
        void purchaseEmoticonItem_userNotFound() {
            // given
            Long userId = 999L;
            Long itemId = 2L;
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> shopService.purchaseItem(userId, itemId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("단일 아이템 조회")
    class GetShopItem {

        @Test
        @DisplayName("EMOTICON 아이템 상세 조회 성공")
        void getShopItem_emoticon() {
            // given
            Long itemId = 2L;
            when(shopItemRepository.findById(itemId)).thenReturn(Optional.of(emoticonItem));

            // when
            ShopItem result = shopService.getShopItem(itemId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getItemName()).isEqualTo("프리미엄 이모티콘");
            assertThat(result.getItemType()).isEqualTo("EMOTICON");
            assertThat(result.getPrice()).isEqualTo(100);
        }

        @Test
        @DisplayName("존재하지 않는 아이템 조회 실패")
        void getShopItem_notFound() {
            // given
            Long itemId = 999L;
            when(shopItemRepository.findById(itemId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> shopService.getShopItem(itemId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITEM_NOT_AVAILABLE);
        }
    }
}
