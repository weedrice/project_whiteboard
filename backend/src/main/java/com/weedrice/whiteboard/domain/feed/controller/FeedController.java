package com.weedrice.whiteboard.domain.feed.controller;

import com.weedrice.whiteboard.domain.feed.dto.FeedResponse;
import com.weedrice.whiteboard.domain.feed.service.FeedService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/feeds")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    @GetMapping
    public ApiResponse<FeedResponse> getMyFeeds(Authentication authentication, Pageable pageable) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        return ApiResponse.success(FeedResponse.from(feedService.getUserFeeds(userId, pageable)));
    }
}
