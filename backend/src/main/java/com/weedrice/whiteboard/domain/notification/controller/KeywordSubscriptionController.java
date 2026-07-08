package com.weedrice.whiteboard.domain.notification.controller;

import com.weedrice.whiteboard.domain.notification.dto.KeywordSubscriptionRequest;
import com.weedrice.whiteboard.domain.notification.dto.KeywordSubscriptionResponse;
import com.weedrice.whiteboard.domain.notification.service.KeywordSubscriptionService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/me/keyword-subscriptions")
@RequiredArgsConstructor
public class KeywordSubscriptionController {

    private final KeywordSubscriptionService keywordSubscriptionService;

    @GetMapping
    public ApiResponse<List<KeywordSubscriptionResponse>> getSubscriptions(@CurrentUserId Long userId) {
        return ApiResponse.success(keywordSubscriptionService.getSubscriptions(userId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KeywordSubscriptionResponse> subscribe(
            @CurrentUserId Long userId,
            @Valid @RequestBody KeywordSubscriptionRequest request) {
        return ApiResponse.success(keywordSubscriptionService.subscribe(userId, request.getKeyword()));
    }

    @DeleteMapping
    public ApiResponse<Void> unsubscribe(
            @CurrentUserId Long userId,
            @Valid @RequestBody KeywordSubscriptionRequest request) {
        keywordSubscriptionService.unsubscribe(userId, request.getKeyword());
        return ApiResponses.ok();
    }
}
