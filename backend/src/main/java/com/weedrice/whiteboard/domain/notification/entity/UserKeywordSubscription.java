package com.weedrice.whiteboard.domain.notification.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_keyword_subscriptions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_keyword_subscriptions_user_keyword", columnNames = {"user_id", "keyword"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserKeywordSubscription extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    private Long subscriptionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "keyword", nullable = false, length = 50)
    private String keyword;

    @Column(name = "last_notified_at")
    private LocalDateTime lastNotifiedAt;

    @Builder
    public UserKeywordSubscription(User user, String keyword) {
        this.user = user;
        this.keyword = keyword;
    }

    public void markNotified(LocalDateTime notifiedAt) {
        this.lastNotifiedAt = notifiedAt;
    }
}
