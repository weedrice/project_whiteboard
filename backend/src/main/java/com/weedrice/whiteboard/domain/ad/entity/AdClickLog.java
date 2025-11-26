package com.weedrice.whiteboard.domain.ad.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "ad_click_logs", indexes = {
        @Index(name = "idx_ad_click_logs_ad", columnList = "ad_id, clicked_at"),
        @Index(name = "idx_ad_click_logs_user", columnList = "user_id")
})
public class AdClickLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ad_id", nullable = false)
    private Ad ad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // 익명 클릭 가능

    @Column(name = "clicked_at", nullable = false)
    private LocalDateTime clickedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Builder
    public AdClickLog(Ad ad, User user, String ipAddress) {
        this.ad = ad;
        this.user = user;
        this.ipAddress = ipAddress;
        this.clickedAt = LocalDateTime.now();
    }
}
