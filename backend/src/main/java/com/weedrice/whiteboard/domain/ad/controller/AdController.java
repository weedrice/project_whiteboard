package com.weedrice.whiteboard.domain.ad.controller;

import com.weedrice.whiteboard.domain.ad.dto.AdResponse;
import com.weedrice.whiteboard.domain.ad.entity.Ad;
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
        Ad ad = adService.getAd(placement);
        return ApiResponse.success(AdResponse.from(ad));
    }

    @PostMapping("/{adId}/click")
    public ApiResponse<String> recordAdClick(
            @PathVariable Long adId,
            Authentication authentication,
            HttpServletRequest request) {
        Long userId = null;
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
            userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        }
        String ipAddress = request.getRemoteAddr();
        String targetUrl = adService.recordAdClick(adId, userId, ipAddress);
        return ApiResponse.success(targetUrl);
    }
}
