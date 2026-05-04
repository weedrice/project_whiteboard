package com.weedrice.whiteboard.domain.ad.controller;

import com.weedrice.whiteboard.domain.ad.dto.AdResponse;
import com.weedrice.whiteboard.domain.ad.service.AdService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
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
        return ApiResponse.success(null);
    }

    @PostMapping("/{adId}/click")
    public ApiResponse<String> recordAdClick(
            @PathVariable Long adId,
            Authentication authentication,
            HttpServletRequest request) {
        String targetUrl = adService.recordAdClick(adId, extractUserId(authentication), request.getRemoteAddr());
        return ApiResponse.success(targetUrl);
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getUserId();
        }
        return null;
    }
}
