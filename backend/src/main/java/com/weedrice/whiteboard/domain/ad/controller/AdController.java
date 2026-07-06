package com.weedrice.whiteboard.domain.ad.controller;

import com.weedrice.whiteboard.domain.ad.dto.AdResponse;
import com.weedrice.whiteboard.domain.ad.service.AdService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.common.util.ClientIpResolver;
import com.weedrice.whiteboard.global.ratelimit.CounterEventGuard;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ads")
@RequiredArgsConstructor
public class AdController {

    private final AdService adService;
    private final ClientIpResolver clientIpResolver;
    private final CounterEventGuard counterEventGuard;

    @GetMapping
    public ApiResponse<AdResponse> getAd(@RequestParam String placement) {
        return ApiResponse.success(adService.getAdResponse(placement));
    }

    @PostMapping("/{adId}/impression")
    public ApiResponse<Void> recordAdImpression(
            @PathVariable Long adId,
            @CurrentUserId(required = false) Long userId,
            HttpServletRequest request) {
        String ipAddress = clientIpResolver.resolve(request);
        if (counterEventGuard.isRecentlyRecorded("ad-impression", adId, userId, ipAddress)) {
            return ApiResponses.ok();
        }
        adService.recordAdImpression(adId);
        counterEventGuard.markRecorded("ad-impression", adId, userId, ipAddress);
        return ApiResponses.ok();
    }

    @PostMapping("/{adId}/click")
    public ApiResponse<String> recordAdClick(
            @PathVariable Long adId,
            @CurrentUserId(required = false) Long userId,
            HttpServletRequest request) {
        String ipAddress = clientIpResolver.resolve(request);
        if (counterEventGuard.isRecentlyRecorded("ad-click", adId, userId, ipAddress)) {
            return ApiResponse.success(adService.getActiveAdTargetUrl(adId));
        }

        String targetUrl = adService.recordAdClick(adId, userId, ipAddress);
        counterEventGuard.markRecorded("ad-click", adId, userId, ipAddress);
        return ApiResponse.success(targetUrl);
    }
}
