package com.weedrice.whiteboard.domain.feed.controller;

import com.weedrice.whiteboard.domain.feed.dto.FeedGenerationRedriveResponse;
import com.weedrice.whiteboard.domain.feed.service.FeedGenerationJobService;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/feed-generation")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
public class AdminFeedGenerationController {
    private final FeedGenerationJobService feedGenerationJobService;

    @PostMapping("/jobs/{jobId}/redrive")
    public ApiResponse<FeedGenerationRedriveResponse> redrive(@PathVariable Long jobId) {
        feedGenerationJobService.redrive(jobId);
        return ApiResponse.success(FeedGenerationRedriveResponse.builder()
                .jobId(jobId)
                .status("PENDING")
                .build());
    }
}
