package com.weedrice.whiteboard.domain.feed.controller;

import com.weedrice.whiteboard.domain.feed.dto.HomeLandingResponse;
import com.weedrice.whiteboard.domain.feed.service.HomeLandingService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeLandingService homeLandingService;

    @GetMapping("/landing")
    public ApiResponse<HomeLandingResponse> getLanding(
            @RequestParam(defaultValue = "24h") String period,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(homeLandingService.getLanding(userDetails, period));
    }
}
