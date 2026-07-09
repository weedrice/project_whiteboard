package com.weedrice.whiteboard.domain.badge.controller;

import com.weedrice.whiteboard.domain.badge.dto.BadgeResponse;
import com.weedrice.whiteboard.domain.badge.dto.RepresentativeBadgeRequest;
import com.weedrice.whiteboard.domain.badge.service.BadgeService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BadgeController {

    private final BadgeService badgeService;

    @GetMapping("/users/{userId}/badges")
    public ApiResponse<List<BadgeResponse>> getUserBadges(@PathVariable Long userId) {
        return ApiResponse.success(badgeService.getUserBadges(userId));
    }

    @GetMapping("/users/me/badges")
    public ApiResponse<List<BadgeResponse>> getMyBadges(@CurrentUserId Long userId) {
        return ApiResponse.success(badgeService.getUserBadges(userId));
    }

    @PutMapping("/users/me/badges/representative")
    public ApiResponse<?> updateRepresentativeBadge(
            @CurrentUserId Long userId,
            @Valid @RequestBody RepresentativeBadgeRequest request) {
        return ApiResponses.ok(badgeService.updateRepresentativeBadge(userId, request.getBadgeCode()));
    }
}
