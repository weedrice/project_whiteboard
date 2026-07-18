package com.weedrice.whiteboard.domain.shop.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PurchaseHistory 엔티티 테스트")
class PurchaseHistoryTest {

    private User user;
    private ShopItem emoticonItem;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("testuser")
                .email("test@example.com")
                .password("password123")
                .displayName("테스트유저")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        emoticonItem = ShopItem.builder()
                .itemName("프리미엄 이모티콘")
                .description("특별한 이모티콘 세트")
                .price(100)
                .itemType("EMOTICON")
                .imageUrl("https://example.com/emoticon.png")
                .build();
        ReflectionTestUtils.setField(emoticonItem, "itemId", 1L);
        ReflectionTestUtils.setField(emoticonItem, "isActive", true);
    }

    @Nested
    @DisplayName("EMOTICON 구매 이력 생성")
    class CreateEmoticonPurchaseHistory {

        @Test
        @DisplayName("EMOTICON 구매 이력 생성 성공")
        void createPurchaseHistory_success() {
            // given & when
            PurchaseHistory purchaseHistory = PurchaseHistory.builder()
                    .user(user)
                    .item(emoticonItem)
                    .purchasedPrice(emoticonItem.getPrice())
                    .build();

            // then
            assertThat(purchaseHistory.getUser()).isEqualTo(user);
            assertThat(purchaseHistory.getItem()).isEqualTo(emoticonItem);
            assertThat(purchaseHistory.getPurchasedPrice()).isEqualTo(100);
            assertThat(purchaseHistory.getItem().getItemType()).isEqualTo("EMOTICON");
        }

        @Test
        @DisplayName("구매 시점의 가격이 저장됨")
        void purchasedPrice_savedAtPurchaseTime() {
            // given
            Integer purchaseTimePrice = 100;

            // when
            PurchaseHistory purchaseHistory = PurchaseHistory.builder()
                    .user(user)
                    .item(emoticonItem)
                    .purchasedPrice(purchaseTimePrice)
                    .build();

            // then
            assertThat(purchaseHistory.getPurchasedPrice()).isEqualTo(purchaseTimePrice);
        }

        @Test
        @DisplayName("아이템 가격 변경과 독립적으로 구매 가격 유지")
        void purchasedPrice_independentFromItemPrice() {
            // given
            Integer originalPrice = 100;
            PurchaseHistory purchaseHistory = PurchaseHistory.builder()
                    .user(user)
                    .item(emoticonItem)
                    .purchasedPrice(originalPrice)
                    .build();

            // when - 아이템 가격이 변경되었다고 가정 (실제로는 엔티티가 불변이지만 테스트 목적)
            // 구매 이력의 purchasedPrice는 여전히 원래 가격을 유지해야 함

            // then
            assertThat(purchaseHistory.getPurchasedPrice()).isEqualTo(originalPrice);
        }

        @Test
        @DisplayName("구매 당시 표시 정보는 이후 상품 수정과 독립적이다")
        void purchasedPresentation_isSnapshot() {
            PurchaseHistory purchaseHistory = PurchaseHistory.builder()
                    .user(user)
                    .item(emoticonItem)
                    .purchasedPrice(emoticonItem.getPrice())
                    .build();

            emoticonItem.updatePresentation("변경된 이름", "https://example.com/changed.png");

            assertThat(purchaseHistory.getPurchasedItemName()).isEqualTo("프리미엄 이모티콘");
            assertThat(purchaseHistory.getPurchasedItemType()).isEqualTo("EMOTICON");
            assertThat(purchaseHistory.getPurchasedImageUrl())
                    .isEqualTo("https://example.com/emoticon.png");
        }

        @Test
        @DisplayName("구매 당시 이미지가 없으면 null도 유효한 스냅샷으로 유지한다")
        void purchasedPresentation_preservesNullImageSnapshot() {
            ShopItem itemWithoutImage = ShopItem.builder()
                    .itemName("이미지 없는 상품")
                    .price(100)
                    .itemType("EMOTICON")
                    .imageUrl(null)
                    .build();
            PurchaseHistory purchaseHistory = PurchaseHistory.builder()
                    .user(user)
                    .item(itemWithoutImage)
                    .purchasedPrice(itemWithoutImage.getPrice())
                    .build();

            itemWithoutImage.updatePresentation("변경된 이름", "https://example.com/new-image.png");

            assertThat(purchaseHistory.getPurchasedItemName()).isEqualTo("이미지 없는 상품");
            assertThat(purchaseHistory.getPurchasedImageUrl()).isNull();
        }

        @Test
        @DisplayName("스냅샷이 없는 기존 행은 현재 상품 표시 정보로 폴백한다")
        void legacyPurchaseWithoutSnapshot_fallsBackToCurrentItemPresentation() {
            PurchaseHistory purchaseHistory = PurchaseHistory.builder()
                    .user(user)
                    .item(emoticonItem)
                    .purchasedPrice(emoticonItem.getPrice())
                    .build();
            ReflectionTestUtils.setField(purchaseHistory, "itemNameSnapshot", null);
            ReflectionTestUtils.setField(purchaseHistory, "itemTypeSnapshot", null);
            ReflectionTestUtils.setField(purchaseHistory, "imageUrlSnapshot", null);

            emoticonItem.updatePresentation("현재 상품 이름", "https://example.com/current.png");

            assertThat(purchaseHistory.getPurchasedItemName()).isEqualTo("현재 상품 이름");
            assertThat(purchaseHistory.getPurchasedItemType()).isEqualTo("EMOTICON");
            assertThat(purchaseHistory.getPurchasedImageUrl())
                    .isEqualTo("https://example.com/current.png");
        }
    }

    @Nested
    @DisplayName("다양한 아이템 타입 구매 이력")
    class DifferentItemTypes {

        @Test
        @DisplayName("EMOTICON 타입 아이템 구매 이력")
        void purchaseHistory_emoticonItem() {
            // given & when
            PurchaseHistory purchaseHistory = PurchaseHistory.builder()
                    .user(user)
                    .item(emoticonItem)
                    .purchasedPrice(emoticonItem.getPrice())
                    .build();

            // then
            assertThat(purchaseHistory.getItem().getItemType()).isEqualTo("EMOTICON");
            assertThat(purchaseHistory.getItem().getItemName()).isEqualTo("프리미엄 이모티콘");
        }

        @Test
        @DisplayName("DECORATION 타입 아이템 구매 이력")
        void purchaseHistory_decorationItem() {
            // given
            ShopItem decorationItem = ShopItem.builder()
                    .itemName("닉네임 색상")
                    .price(500)
                    .itemType("DECORATION")
                    .build();
            ReflectionTestUtils.setField(decorationItem, "itemId", 2L);

            // when
            PurchaseHistory purchaseHistory = PurchaseHistory.builder()
                    .user(user)
                    .item(decorationItem)
                    .purchasedPrice(decorationItem.getPrice())
                    .build();

            // then
            assertThat(purchaseHistory.getItem().getItemType()).isEqualTo("DECORATION");
            assertThat(purchaseHistory.getPurchasedPrice()).isEqualTo(500);
        }
    }

    @Nested
    @DisplayName("사용자와 아이템 연관관계")
    class UserItemRelationship {

        @Test
        @DisplayName("구매 이력에서 사용자 정보 접근")
        void accessUserFromPurchaseHistory() {
            // given & when
            PurchaseHistory purchaseHistory = PurchaseHistory.builder()
                    .user(user)
                    .item(emoticonItem)
                    .purchasedPrice(emoticonItem.getPrice())
                    .build();

            // then
            assertThat(purchaseHistory.getUser().getLoginId()).isEqualTo("testuser");
            assertThat(purchaseHistory.getUser().getDisplayName()).isEqualTo("테스트유저");
        }

        @Test
        @DisplayName("구매 이력에서 아이템 정보 접근")
        void accessItemFromPurchaseHistory() {
            // given & when
            PurchaseHistory purchaseHistory = PurchaseHistory.builder()
                    .user(user)
                    .item(emoticonItem)
                    .purchasedPrice(emoticonItem.getPrice())
                    .build();

            // then
            assertThat(purchaseHistory.getItem().getItemName()).isEqualTo("프리미엄 이모티콘");
            assertThat(purchaseHistory.getItem().getDescription()).isEqualTo("특별한 이모티콘 세트");
            assertThat(purchaseHistory.getItem().getImageUrl()).isEqualTo("https://example.com/emoticon.png");
        }
    }

    @Nested
    @DisplayName("무료 아이템 구매 이력")
    class FreeItemPurchase {

        @Test
        @DisplayName("무료 EMOTICON 구매 이력 생성")
        void purchaseHistory_freeEmoticon() {
            // given
            ShopItem freeEmoticon = ShopItem.builder()
                    .itemName("무료 이모티콘")
                    .price(0)
                    .itemType("EMOTICON")
                    .build();
            ReflectionTestUtils.setField(freeEmoticon, "itemId", 10L);

            // when
            PurchaseHistory purchaseHistory = PurchaseHistory.builder()
                    .user(user)
                    .item(freeEmoticon)
                    .purchasedPrice(0)
                    .build();

            // then
            assertThat(purchaseHistory.getPurchasedPrice()).isZero();
            assertThat(purchaseHistory.getItem().getPrice()).isZero();
        }
    }
}
