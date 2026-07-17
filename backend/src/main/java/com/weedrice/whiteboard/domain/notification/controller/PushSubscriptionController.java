package com.weedrice.whiteboard.domain.notification.controller;

import com.weedrice.whiteboard.domain.notification.dto.PushSubscriptionRequest;
import com.weedrice.whiteboard.domain.notification.dto.PushSubscriptionResponse;
import com.weedrice.whiteboard.domain.notification.service.PushSubscriptionService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/push-subscriptions")
@RequiredArgsConstructor
public class PushSubscriptionController {

    private final PushSubscriptionService pushSubscriptionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PushSubscriptionResponse> subscribe(
            @CurrentUserId Long userId,
            @Valid @RequestBody PushSubscriptionRequest request) {
        return ApiResponse.success(pushSubscriptionService.subscribe(userId, request));
    }

    @DeleteMapping
    public ApiResponse<Void> unsubscribe(
            @CurrentUserId Long userId,
            @Valid @RequestBody PushSubscriptionRequest request) {
        pushSubscriptionService.unsubscribe(userId, request);
        return ApiResponses.ok();
    }

    @DeleteMapping("/all")
    public ApiResponse<Void> unsubscribeAll(@CurrentUserId Long userId) {
        pushSubscriptionService.unsubscribeAll(userId);
        return ApiResponses.ok();
    }
}
