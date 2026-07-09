package com.weedrice.whiteboard.domain.badge.entity;

import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "badges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Badge extends BaseTimeEntity {

    @Id
    @Column(name = "badge_code", length = 50)
    private String badgeCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "icon", nullable = false, length = 80)
    private String icon;

    @Column(name = "tier", nullable = false, length = 20)
    private String tier;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
