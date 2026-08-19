package com.weedrice.whiteboard.domain.shop.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ShopItem 엔티티 테스트")
class ShopItemTest {

    @Nested
    @DisplayName("EMOTICON 타입 아이템 생성")
    class EmoticonItemCreation {

        @Test
        @DisplayName("EMOTICON 타입 아이템 생성 성공")
        void createEmoticonItem_success() {
            // given
            String itemName = "프리미엄 이모티콘 세트";
            String description = "특별한 이모티콘 10종 세트";
            Integer price = 100;
            String itemType = "EMOTICON";
            String imageUrl = "https://example.com/emoticon.png";

            // when
            ShopItem emoticonItem = ShopItem.builder()
                    .itemName(itemName)
                    .description(description)
                    .price(price)
                    .itemType(itemType)
                    .imageUrl(imageUrl)
                    .build();

            // then
            assertThat(emoticonItem.getItemName()).isEqualTo(itemName);
            assertThat(emoticonItem.getDescription()).isEqualTo(description);
            assertThat(emoticonItem.getPrice()).isEqualTo(price);
            assertThat(emoticonItem.getItemType()).isEqualTo("EMOTICON");
            assertThat(emoticonItem.getImageUrl()).isEqualTo(imageUrl);
            assertThat(emoticonItem.getIsActive()).isTrue();
            assertThat(emoticonItem.getIsSaleEnabled()).isTrue();
            assertThat(emoticonItem.isPurchasable()).isTrue();
        }

        @Test
        @DisplayName("EMOTICON 아이템 - 설명 없이 생성 가능")
        void createEmoticonItem_withoutDescription() {
            // given & when
            ShopItem emoticonItem = ShopItem.builder()
                    .itemName("기본 이모티콘")
                    .price(50)
                    .itemType("EMOTICON")
                    .build();

            // then
            assertThat(emoticonItem.getItemName()).isEqualTo("기본 이모티콘");
            assertThat(emoticonItem.getDescription()).isNull();
            assertThat(emoticonItem.getPrice()).isEqualTo(50);
            assertThat(emoticonItem.getItemType()).isEqualTo("EMOTICON");
        }

        @Test
        @DisplayName("EMOTICON 아이템 - 이미지 URL 없이 생성 가능")
        void createEmoticonItem_withoutImageUrl() {
            // given & when
            ShopItem emoticonItem = ShopItem.builder()
                    .itemName("텍스트 이모티콘")
                    .description("텍스트 기반 이모티콘")
                    .price(30)
                    .itemType("EMOTICON")
                    .build();

            // then
            assertThat(emoticonItem.getItemName()).isEqualTo("텍스트 이모티콘");
            assertThat(emoticonItem.getImageUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("판매 상태")
    class SaleStatus {

        @Test
        @DisplayName("원본 재활성화는 관리자 판매 중지를 해제하지 않는다")
        void sourceReactivation_doesNotResumeSuspendedSale() {
            ShopItem item = ShopItem.builder()
                    .itemName("운영 중지 아이템")
                    .price(100)
                    .itemType("EMOTICON")
                    .targetId(1L)
                    .build();

            item.suspendSale();
            item.deactivate();
            item.activate();

            assertThat(item.getIsActive()).isTrue();
            assertThat(item.getIsSaleEnabled()).isFalse();
            assertThat(item.isPurchasable()).isFalse();
        }

        @Test
        @DisplayName("판매 재개는 원본 비활성 상태를 바꾸지 않는다")
        void resumeSale_doesNotActivateSource() {
            ShopItem item = ShopItem.builder()
                    .itemName("비공개 아이템")
                    .price(100)
                    .itemType("EMOTICON")
                    .targetId(1L)
                    .build();
            item.deactivate();
            item.suspendSale();

            item.resumeSale();

            assertThat(item.getIsActive()).isFalse();
            assertThat(item.getIsSaleEnabled()).isTrue();
            assertThat(item.isPurchasable()).isFalse();
        }

        @Test
        @DisplayName("폐기하면 원본과 판매 상태를 모두 비활성화한다")
        void retire_disablesSourceAndSale() {
            ShopItem item = ShopItem.builder()
                    .itemName("폐기 아이템")
                    .price(100)
                    .itemType("EMOTICON")
                    .targetId(1L)
                    .build();

            item.retire();

            assertThat(item.getIsActive()).isFalse();
            assertThat(item.getIsSaleEnabled()).isFalse();
            assertThat(item.getTargetId()).isNull();
            assertThat(item.isPurchasable()).isFalse();
        }
    }

    @Nested
    @DisplayName("다른 타입 아이템과 구분")
    class ItemTypeDistinction {

        @Test
        @DisplayName("EMOTICON 타입과 DECORATION 타입 구분")
        void distinguishEmoticonFromDecoration() {
            // given & when
            ShopItem emoticonItem = ShopItem.builder()
                    .itemName("웃는 이모티콘")
                    .price(50)
                    .itemType("EMOTICON")
                    .build();

            ShopItem decorationItem = ShopItem.builder()
                    .itemName("닉네임 색상")
                    .price(100)
                    .itemType("DECORATION")
                    .build();

            // then
            assertThat(emoticonItem.getItemType()).isEqualTo("EMOTICON");
            assertThat(decorationItem.getItemType()).isEqualTo("DECORATION");
            assertThat(emoticonItem.getItemType()).isNotEqualTo(decorationItem.getItemType());
        }

        @Test
        @DisplayName("EMOTICON 타입과 BADGE 타입 구분")
        void distinguishEmoticonFromBadge() {
            // given & when
            ShopItem emoticonItem = ShopItem.builder()
                    .itemName("하트 이모티콘")
                    .price(30)
                    .itemType("EMOTICON")
                    .build();

            ShopItem badgeItem = ShopItem.builder()
                    .itemName("VIP 뱃지")
                    .price(500)
                    .itemType("BADGE")
                    .build();

            // then
            assertThat(emoticonItem.getItemType()).isEqualTo("EMOTICON");
            assertThat(badgeItem.getItemType()).isEqualTo("BADGE");
        }
    }

    @Nested
    @DisplayName("EMOTICON 아이템 가격 테스트")
    class EmoticonPriceTest {

        @Test
        @DisplayName("무료 EMOTICON 아이템 생성 가능")
        void createFreeEmoticon() {
            // given & when
            ShopItem freeEmoticon = ShopItem.builder()
                    .itemName("무료 이모티콘")
                    .price(0)
                    .itemType("EMOTICON")
                    .build();

            // then
            assertThat(freeEmoticon.getPrice()).isZero();
        }

        @Test
        @DisplayName("다양한 가격대의 EMOTICON 아이템 생성")
        void createEmoticonWithVariousPrices() {
            // given & when
            ShopItem basicEmoticon = ShopItem.builder()
                    .itemName("기본 이모티콘")
                    .price(10)
                    .itemType("EMOTICON")
                    .build();

            ShopItem premiumEmoticon = ShopItem.builder()
                    .itemName("프리미엄 이모티콘")
                    .price(100)
                    .itemType("EMOTICON")
                    .build();

            ShopItem specialEmoticon = ShopItem.builder()
                    .itemName("한정판 이모티콘")
                    .price(500)
                    .itemType("EMOTICON")
                    .build();

            // then
            assertThat(basicEmoticon.getPrice()).isEqualTo(10);
            assertThat(premiumEmoticon.getPrice()).isEqualTo(100);
            assertThat(specialEmoticon.getPrice()).isEqualTo(500);
        }
    }
}
