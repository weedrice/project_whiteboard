package com.weedrice.whiteboard.domain.ad.entity;

import com.weedrice.whiteboard.global.common.converter.BooleanToYNConverter;
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
@Table(name = "ads", indexes = {
        @Index(name = "idx_ads_active", columnList = "is_active, placement, start_date, end_date")
})
public class Ad extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ad_id")
    private Long adId;

    @Column(name = "ad_name", length = 100, nullable = false)
    private String adName;

    @Column(name = "image_url", length = 255, nullable = false)
    private String imageUrl;

    @Column(name = "placement", length = 100, nullable = false)
    private String placement; // HEADER, SIDEBAR, CONTENT

    @Column(name = "target_url", length = 255, nullable = false)
    private String targetUrl;

    @Column(name = "impression_count", nullable = false)
    private Integer impressionCount;

    @Column(name = "click_count", nullable = false)
    private Integer clickCount;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_active", length = 1, nullable = false)
    private Boolean isActive;

    @Builder
    public Ad(String adName, String imageUrl, String placement, String targetUrl, LocalDateTime startDate, LocalDateTime endDate) {
        this.adName = adName;
        this.imageUrl = imageUrl;
        this.placement = placement;
        this.targetUrl = targetUrl;
        this.startDate = startDate;
        this.endDate = endDate;
        this.impressionCount = 0;
        this.clickCount = 0;
        this.isActive = true;
    }

    public void incrementImpressionCount() {
        this.impressionCount++;
    }

    public void incrementClickCount() {
        this.clickCount++;
    }
}
