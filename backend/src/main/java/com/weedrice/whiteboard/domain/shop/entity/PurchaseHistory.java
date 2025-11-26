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

    @Builder
    public PurchaseHistory(User user, ShopItem item, Integer purchasedPrice) {
        this.user = user;
        this.item = item;
        this.purchasedPrice = purchasedPrice;
    }
}
