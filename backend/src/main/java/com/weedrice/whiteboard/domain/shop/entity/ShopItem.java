package com.weedrice.whiteboard.domain.shop.entity;

import com.weedrice.whiteboard.global.common.converter.BooleanToYNConverter;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "shop_items", indexes = {
        @Index(name = "idx_shop_items_active", columnList = "is_active, item_type")
})
public class ShopItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "item_name", length = 100, nullable = false)
    private String itemName;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Column(name = "item_type", length = 50, nullable = false)
    private String itemType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_active", length = 1, nullable = false)
    private Boolean isActive;

    @Builder
    public ShopItem(String itemName, String description, Integer price, String itemType, Long targetId, String imageUrl) {
        this.itemName = itemName;
        this.description = description;
        this.price = price;
        this.itemType = itemType;
        this.targetId = targetId;
        this.imageUrl = imageUrl;
        this.isActive = true;
    }

    public void updatePresentation(String itemName, String imageUrl) {
        this.itemName = itemName;
        this.imageUrl = imageUrl;
    }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void retire() {
        this.isActive = false;
        this.targetId = null;
    }
}
