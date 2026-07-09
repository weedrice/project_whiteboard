package com.weedrice.whiteboard.domain.badge.dto;

import com.weedrice.whiteboard.domain.badge.entity.Badge;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BadgeCompactResponse {
    private String badgeCode;
    private String name;
    private String icon;
    private String tier;

    public static BadgeCompactResponse from(Badge badge) {
        if (badge == null) {
            return null;
        }
        return BadgeCompactResponse.builder()
                .badgeCode(badge.getBadgeCode())
                .name(badge.getName())
                .icon(badge.getIcon())
                .tier(badge.getTier())
                .build();
    }
}
