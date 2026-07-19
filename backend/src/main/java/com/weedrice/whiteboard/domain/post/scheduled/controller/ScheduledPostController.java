package com.weedrice.whiteboard.domain.post.scheduled.controller;

import com.weedrice.whiteboard.domain.post.scheduled.dto.ScheduledPostRequest;
import com.weedrice.whiteboard.domain.post.scheduled.dto.ScheduledPostDetailResponse;
import com.weedrice.whiteboard.domain.post.scheduled.dto.ScheduledPostResponse;
import com.weedrice.whiteboard.domain.post.scheduled.service.ScheduledPostService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.jspecify.annotations.NonNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ScheduledPostController {

    private final ScheduledPostService scheduledPostService;

    @PostMapping("/boards/{boardUrl}/scheduled-posts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ScheduledPostResponse> createScheduledPost(
            @PathVariable String boardUrl,
            @CurrentUserId Long userId,
            @Valid @RequestBody ScheduledPostRequest request) {
        return ApiResponse.success(scheduledPostService.create(userId, boardUrl, request));
    }

    @GetMapping("/users/me/scheduled-posts")
    public ApiResponse<PageResponse<ScheduledPostResponse>> getMyScheduledPosts(
            @CurrentUserId Long userId,
            @NonNull Pageable pageable) {
        Page<ScheduledPostResponse> responsePage = scheduledPostService.getMine(userId, pageable);
        return ApiResponses.page(responsePage);
    }

    @GetMapping("/scheduled-posts/{scheduledPostId}")
    public ApiResponse<ScheduledPostDetailResponse> getScheduledPost(
            @PathVariable Long scheduledPostId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(scheduledPostService.getOwned(userId, scheduledPostId));
    }

    @PutMapping("/scheduled-posts/{scheduledPostId}")
    public ApiResponse<ScheduledPostResponse> updateScheduledPost(
            @PathVariable Long scheduledPostId,
            @CurrentUserId Long userId,
            @Valid @RequestBody ScheduledPostRequest request) {
        return ApiResponse.success(scheduledPostService.update(userId, scheduledPostId, request));
    }

    @DeleteMapping("/scheduled-posts/{scheduledPostId}")
    public ApiResponse<Void> cancelScheduledPost(
            @PathVariable Long scheduledPostId,
            @CurrentUserId Long userId) {
        scheduledPostService.cancel(userId, scheduledPostId);
        return ApiResponses.ok();
    }
}
