package com.weedrice.whiteboard.domain.ad.controller;

import com.weedrice.whiteboard.domain.ad.dto.AdResponse;
import com.weedrice.whiteboard.domain.ad.service.AdService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.common.util.ClientUtils;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ads")
@RequiredArgsConstructor
public class AdController {

    private final AdService adService;

    @GetMapping
    public ApiResponse<AdResponse> getAd(@RequestParam String placement) {
        return ApiResponse.success(adService.getAdResponse(placement));
    }

    @PostMapping("/{adId}/impression")
    public ApiResponse<Void> recordAdImpression(
            @PathVariable Long adId) {
        adService.recordAdImpression(adId);
        return ApiResponses.ok();
    }

    @PostMapping("/{adId}/click")
    public ApiResponse<String> recordAdClick(
            @PathVariable Long adId,
            HttpServletRequest request) {
        String targetUrl = adService.recordAdClick(
                adId,
                SecurityUtils.getCurrentUserIdOrNull(),
                ClientUtils.getIp(request));
        return ApiResponse.success(targetUrl);
    }
}
