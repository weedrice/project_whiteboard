package com.weedrice.whiteboard.domain.emoticon.entity;

import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

/**
 * 이모티콘 구매 이력 엔티티
 * - PurchaseHistory(상점 아이템 구매)와 유사한 구조
 * - point_histories 테이블에 포인트 변동 이력이 별도 기록됨
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "emoticon_purchases", indexes = {
        @Index(name = "idx_emoticon_purchases_user", columnList = "user_id, created_at DESC"),
        @Index(name = "idx_emoticon_purchases_emoticon", columnList = "emoticon_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_emoticon_purchases_user_emoticon", columnNames = {"user_id", "emoticon_id"})
})
public class EmoticonPurchase extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_id")
    private Long purchaseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emoticon_id", nullable = false)
    private EmoticonMaster emoticon;

    /**
     * 구매 시점의 가격 (PurchaseHistory.purchasedPrice와 동일한 개념)
     */
    @Column(name = "purchased_price", nullable = false)
    private Integer purchasedPrice;

    @Builder
    public EmoticonPurchase(User user, EmoticonMaster emoticon, Integer purchasedPrice) {
        this.user = user;
        this.emoticon = emoticon;
        this.purchasedPrice = purchasedPrice;
    }
}
