package com.weedrice.whiteboard.domain.badge.controller;

import com.weedrice.whiteboard.domain.badge.dto.BadgeBackfillResponse;
import com.weedrice.whiteboard.domain.badge.service.BadgeService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import com.weedrice.whiteboard.global.security.SuperAdminPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/badges")
@RequiredArgsConstructor
public class BadgeAdminController {

    private final BadgeService badgeService;
    private final SuperAdminPolicy superAdminPolicy;

    @PostMapping("/backfill")
    public ApiResponse<BadgeBackfillResponse> backfillBadges(@CurrentUserId Long userId) {
        superAdminPolicy.requireUsableSuperAdmin(userId);
        return ApiResponse.success(badgeService.backfillAll());
    }
}
