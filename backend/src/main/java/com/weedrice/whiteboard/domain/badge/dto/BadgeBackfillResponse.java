package com.weedrice.whiteboard.domain.badge.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BadgeBackfillResponse {
    private long scannedUsers;
    private long awardedBadges;
}
