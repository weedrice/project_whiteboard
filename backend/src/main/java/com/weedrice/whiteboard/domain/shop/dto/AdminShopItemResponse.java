package com.weedrice.whiteboard.domain.shop.dto;

import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminShopItemResponse {

    private Long itemId;
    private String itemName;
    private String description;
    private Integer price;
    private String itemType;
    private Long targetId;
    private String imageUrl;
    private Boolean isActive;
    private Boolean isSaleEnabled;
    private boolean purchasable;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static AdminShopItemResponse from(ShopItem item) {
        return AdminShopItemResponse.builder()
                .itemId(item.getItemId())
                .itemName(item.getItemName())
                .description(item.getDescription())
                .price(item.getPrice())
                .itemType(item.getItemType())
                .targetId(item.getTargetId())
                .imageUrl(item.getImageUrl())
                .isActive(item.getIsActive())
                .isSaleEnabled(item.getIsSaleEnabled())
                .purchasable(item.isPurchasable())
                .createdAt(item.getCreatedAt())
                .modifiedAt(item.getModifiedAt())
                .build();
    }
}
