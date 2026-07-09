package com.weedrice.whiteboard.domain.badge.dto;

import com.weedrice.whiteboard.domain.badge.entity.Badge;
import com.weedrice.whiteboard.domain.badge.entity.UserBadge;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class BadgeResponse {
    private String badgeCode;
    private String name;
    private String description;
    private String icon;
    private String tier;
    private LocalDateTime acquiredAt;
    private boolean acquired;
    private boolean representative;

    public static BadgeResponse from(Badge badge, UserBadge userBadge, String representativeBadgeCode) {
        boolean acquired = userBadge != null;
        return BadgeResponse.builder()
                .badgeCode(badge.getBadgeCode())
                .name(badge.getName())
                .description(badge.getDescription())
                .icon(badge.getIcon())
                .tier(badge.getTier())
                .acquiredAt(acquired ? userBadge.getAcquiredAt() : null)
                .acquired(acquired)
                .representative(acquired && badge.getBadgeCode().equals(representativeBadgeCode))
                .build();
    }
}
