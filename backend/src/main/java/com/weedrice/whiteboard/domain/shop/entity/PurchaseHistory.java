package com.weedrice.whiteboard.domain.shop.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "purchase_history", indexes = {
        @Index(name = "idx_purchase_history_user", columnList = "user_id, created_at DESC")
})
public class PurchaseHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_id")
    private Long purchaseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private ShopItem item;

    @Column(name = "purchased_price", nullable = false)
    private Integer purchasedPrice;

    @Column(name = "item_name_snapshot", length = 100)
    private String itemNameSnapshot;

    @Column(name = "item_type_snapshot", length = 50)
    private String itemTypeSnapshot;

    @Column(name = "image_url_snapshot", length = 500)
    private String imageUrlSnapshot;

    @Builder
    public PurchaseHistory(User user, ShopItem item, Integer purchasedPrice) {
        this.user = user;
        this.item = item;
        this.purchasedPrice = purchasedPrice;
        this.itemNameSnapshot = item.getItemName();
        this.itemTypeSnapshot = item.getItemType();
        this.imageUrlSnapshot = item.getImageUrl();
    }

    public String getPurchasedItemName() {
        return itemNameSnapshot != null ? itemNameSnapshot : item.getItemName();
    }

    public String getPurchasedItemType() {
        return itemNameSnapshot != null ? itemTypeSnapshot : item.getItemType();
    }

    public String getPurchasedImageUrl() {
        return itemNameSnapshot != null ? imageUrlSnapshot : item.getImageUrl();
    }
}
